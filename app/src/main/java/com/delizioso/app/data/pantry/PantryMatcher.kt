package com.delizioso.app.data.pantry

import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.RecipeWithDetails

data class RecipePantryMatch(
    val details: RecipeWithDetails,
    val matchPercentage: Int,
    val matchedIngredients: List<String>,
    val missingIngredients: List<String>,
    val totalIngredients: Int,
    val isReadyToCook: Boolean,
)

object PantryMatcher {

    private val STRIP_WORDS = setOf(
        "fresh", "fresco", "fresca", "freschi", "fresche",
        "diced", "cubed", "tagliato", "tagliata", "tritato", "tritata", "chopped", "minced",
        "grated", "grattugiato", "grattugiata",
        "sliced", "affettato", "affettata", "whole", "intero", "intera", "interi", "intere",
        "extra", "virgin", "vergine", "extravergine",
        "organic", "bio", "biologico", "biologica",
        "crushed", "ground", "macinato", "macinata",
        "cooked", "cotto", "cotta", "cotti", "cotte", "raw", "crudo", "cruda",
        "medium", "large", "small", "grande", "grandi", "piccolo", "piccoli", "medio", "media", "medie", "medi",
        "pelati", "pelato", "pelata", "pelate", "canned", "scatola", "barattolo", "secco", "secchi", "secca", "secche",
        "dorata", "dorate", "dorato", "dorati", "rosso", "rossa", "rossi", "rosse", "bianco", "bianca", "bianchi", "bianche",
        "di", "del", "della", "dello", "dei", "degli", "delle", "a", "al", "alla", "con", "senza", "per", "in", "cubetti", "fette",
    )

    private val ITALIAN_PLURALS = mapOf(
        "uova" to "uovo",
        "pomodori" to "pomodoro",
        "cipolle" to "cipolla",
        "carote" to "carota",
        "patate" to "patata",
        "zucchine" to "zucchina",
        "melanzane" to "melanzana",
        "funghi" to "fungo",
        "spicchi" to "spicchio",
        "mele" to "mela",
        "limoni" to "limone",
        "peperoni" to "peperone",
        "pomodorini" to "pomodorino",
    )

    fun normalize(name: String): String {
        var clean = name.lowercase()
            .replace(Regex("[^a-zA-Z0-9àèéìòùáéíóú\\s]"), " ")
            .trim()

        // Replace common Italian plurals
        ITALIAN_PLURALS.forEach { (plural, singular) ->
            clean = clean.replace(Regex("\\b$plural\\b"), singular)
        }

        // Remove trailing 's' for English plurals (e.g. eggs -> egg, carrots -> carrot)
        val tokens = clean.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STRIP_WORDS }
            .map { token ->
                if (token.endsWith("s") && token.length > 3 && !token.endsWith("ss") && !token.endsWith("us") && !token.endsWith("is")) {
                    token.dropLast(1)
                } else {
                    token
                }
            }

        return tokens.joinToString(" ")
    }

    fun isIngredientAvailable(recipeIngredientName: String, inStockItems: List<PantryItemEntity>): Boolean {
        val normRecipe = normalize(recipeIngredientName)
        if (normRecipe.isBlank()) return false

        return inStockItems.any { item ->
            if (!item.inStock) return@any false
            val normPantry = normalize(item.name)
            if (normPantry.isBlank()) return@any false

            normRecipe.contains(normPantry) ||
                    normPantry.contains(normRecipe) ||
                    normRecipe.split(" ").any { rToken -> rToken.length > 2 && normPantry.contains(rToken) } ||
                    normPantry.split(" ").any { pToken -> pToken.length > 2 && normRecipe.contains(pToken) }
        }
    }

    fun match(recipe: RecipeWithDetails, inStockItems: List<PantryItemEntity>): RecipePantryMatch {
        val activePantry = inStockItems.filter { it.inStock }
        val ingredients = recipe.ingredients

        if (ingredients.isEmpty()) {
            return RecipePantryMatch(
                details = recipe,
                matchPercentage = 100,
                matchedIngredients = emptyList(),
                missingIngredients = emptyList(),
                totalIngredients = 0,
                isReadyToCook = true,
            )
        }

        val matched = mutableListOf<String>()
        val missing = mutableListOf<String>()

        ingredients.forEach { ing ->
            if (isIngredientAvailable(ing.name, activePantry)) {
                matched.add(ing.name)
            } else {
                missing.add(ing.name)
            }
        }

        val total = ingredients.size
        val pct = ((matched.size.toDouble() / total.toDouble()) * 100).toInt()

        return RecipePantryMatch(
            details = recipe,
            matchPercentage = pct,
            matchedIngredients = matched,
            missingIngredients = missing,
            totalIngredients = total,
            isReadyToCook = missing.isEmpty(),
        )
    }

    fun rank(recipes: List<RecipeWithDetails>, inStockItems: List<PantryItemEntity>): List<RecipePantryMatch> {
        return recipes.map { match(it, inStockItems) }
            .sortedWith(
                compareByDescending<RecipePantryMatch> { it.matchPercentage }
                    .thenBy { it.missingIngredients.size }
                    .thenByDescending { it.details.recipe.isFavorite }
                    .thenBy { it.details.recipe.title }
            )
    }
}
