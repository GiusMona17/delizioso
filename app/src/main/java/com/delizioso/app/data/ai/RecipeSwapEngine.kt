package com.delizioso.app.data.ai

import com.delizioso.app.R
import com.delizioso.app.data.local.PantryItemEntity

enum class SwapPreset(
    val titleRes: Int,
    val iconEmoji: String,
) {
    VEGAN(R.string.swap_vegan, "🌱"),
    GLUTEN_FREE(R.string.swap_gluten_free, "🌾"),
    DAIRY_FREE(R.string.swap_dairy_free, "🥛"),
    LOW_CARB(R.string.swap_low_carb, "🥑"),
    PANTRY(R.string.swap_pantry, "🧊"),
    SIMPLIFY(R.string.swap_simplify, "⚡"),
    SWEET_TWIST(R.string.swap_twist, "✨"),
}

object RecipeSwapEngine {

    fun buildPrompt(preset: SwapPreset, pantryItems: List<PantryItemEntity> = emptyList()): String =
        when (preset) {
            SwapPreset.VEGAN ->
                "How can I make this recipe completely vegan? Please specify exact ingredient substitutions."
            SwapPreset.GLUTEN_FREE ->
                "How can I make this recipe gluten-free? Suggest substitutions for any gluten-containing ingredients."
            SwapPreset.DAIRY_FREE ->
                "What are the best dairy-free substitutes for this recipe?"
            SwapPreset.LOW_CARB ->
                "How can I reduce the carbs or make a low-carb variation of this dish?"
            SwapPreset.PANTRY -> {
                val available = pantryItems.filter { it.inStock }.map { it.name }.distinct()
                if (available.isNotEmpty()) {
                    "Here are the ingredients in my pantry: ${available.joinToString(", ")}. How can I adapt this recipe using what I have?"
                } else {
                    "What common kitchen pantry staples can I use to substitute missing ingredients in this recipe?"
                }
            }
            SwapPreset.SIMPLIFY ->
                "How can I make a simpler, quicker version of this recipe in fewer steps?"
            SwapPreset.SWEET_TWIST ->
                "Suggest a creative chef variation or flavor twist on this classic dish."
        }
}
