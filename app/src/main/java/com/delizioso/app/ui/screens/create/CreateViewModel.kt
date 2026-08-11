package com.delizioso.app.ui.screens.create

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.R
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.AiUnavailableException
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.ai.OcrTextExtractor
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.Platform
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class CreateBusy { OCR, STRUCTURING }

data class CreateUiState(
    val photoPath: String? = null,
    val busy: CreateBusy? = null,
    val error: String? = null,
    /** Filled by the OCR→AI flow; the screen applies it to the form. */
    val draft: StructuredRecipe? = null,
)

class CreateViewModel(
    private val resources: Resources,
    private val ocr: OcrTextExtractor,
    private val structurer: NanoStructurer,
    private val repository: RecipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    fun onPhotoPicked(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ImageStore.saveToInternal(context, uri) }
                .onSuccess { path -> _state.update { it.copy(photoPath = path, error = null) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: resources.getString(R.string.create_photo_save_error)) } }
        }
    }

    /** OCR the picked photo, then structure with on-device AI, then fill the form. */
    fun scanCookbook() {
        val path = _state.value.photoPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = CreateBusy.OCR, error = null, draft = null) }
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeSampledBitmap(File(path)) }
                val text = ocr.recognize(bitmap)
                if (text.isBlank()) {
                    _state.update { it.copy(busy = null, error = resources.getString(R.string.create_ocr_no_text)) }
                    return@launch
                }
                _state.update { it.copy(busy = CreateBusy.STRUCTURING) }
                val recipe = structurer.structure(text)
                _state.update { it.copy(busy = null, draft = recipe) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiUnavailableException) {
                _state.update { it.copy(busy = null, error = resources.getString(R.string.create_ai_unavailable)) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, error = e.message ?: resources.getString(R.string.create_scan_failed)) }
            }
        }
    }

    suspend fun save(recipe: StructuredRecipe, photoPath: String?, tags: List<String> = emptyList()): Long {
        val details = RecipeWithDetails(
            recipe = RecipeEntity(
                title = recipe.title.orEmpty(),
                description = recipe.description,
                servings = recipe.servings,
                prepTimeMinutes = recipe.prepTimeMinutes,
                cookTimeMinutes = recipe.cookTimeMinutes,
                imageUri = photoPath,
            ),
            ingredients = recipe.ingredients.mapIndexed { i, ing -> ing.copy(recipeId = 0, position = i) },
            steps = recipe.steps.mapIndexed { i, s -> StepEntity(recipeId = 0, position = i + 1, text = s) },
            source = SourceEntity(recipeId = 0, platform = Platform.OCR),
        )
        return repository.save(details, tags)
    }

    private fun decodeSampledBitmap(file: File, maxDim: Int = 1280): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IOException(resources.getString(R.string.create_decode_error))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                CreateViewModel(
                    resources = app.resources,
                    ocr = app.container.ocrTextExtractor,
                    structurer = app.container.nanoStructurer,
                    repository = app.container.recipeRepository,
                )
            }
        }
    }
}
