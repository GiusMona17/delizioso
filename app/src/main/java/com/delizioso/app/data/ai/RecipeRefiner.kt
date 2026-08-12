package com.delizioso.app.data.ai

import com.delizioso.app.data.UnitConverter
import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Progress of a "convert units and translate" pass. */
sealed interface RefineState {
    data object Idle : RefineState
    data object Running : RefineState
    data class Failed(val message: String) : RefineState
    /** Converted, but the recipe was already in the user's language. */
    data object NothingToTranslate : RefineState
}

/**
 * Converts imperial amounts in code, then translates the wording with ML Kit.
 *
 * Owned by whichever screen offers the action — import preview and edit both do,
 * because a recipe in cups is no less annoying once it is saved, and a recipe
 * typed in by hand can want the same treatment as an imported one.
 *
 * The two halves are applied separately on purpose: conversion is exact and
 * instant, so it lands even when the language pack cannot be downloaded.
 */
class RecipeRefiner(private val translator: RecipeTranslator) {

    private val _state = MutableStateFlow<RefineState>(RefineState.Idle)
    val state: StateFlow<RefineState> = _state.asStateFlow()

    fun clearError() {
        _state.value = RefineState.Idle
    }

    fun refine(
        scope: CoroutineScope,
        recipe: StructuredRecipe,
        onRefined: (StructuredRecipe) -> Unit,
    ) {
        if (_state.value is RefineState.Running) return
        scope.launch {
            _state.value = RefineState.Running
            val converted = UnitConverter.convert(recipe)
            onRefined(converted)
            try {
                onRefined(translator.translate(converted))
                _state.value = RefineState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecipeTranslator.AlreadyInTargetLanguage) {
                _state.value = RefineState.NothingToTranslate
            } catch (e: Exception) {
                _state.value = RefineState.Failed(e.message ?: "Translation failed")
            }
        }
    }
}
