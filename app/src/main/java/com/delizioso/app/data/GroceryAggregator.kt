package com.delizioso.app.data

import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.pantry.PantryMatcher
import com.delizioso.app.R

/** A consolidated grocery-list line. */
data class GroceryItem(
    val name: String,
    /** Final display line, e.g. "500 g pasta" or "6 uova". */
    val line: String,
    /** True when several planned meals were merged into this line. */
    val isMerged: Boolean,
    /** Titles of the planned recipes this line came from (empty for custom items). */
    val recipeTitles: List<String> = emptyList(),
    /** Supermarket aisle, used by the "By Category" view. */
    val category: String = GroceryCategories.OTHER,
    /** True if this item is currently in stock in the Smart Pantry. */
    val inPantry: Boolean = false,
)

/**
 * Aggregates ingredients across the planned recipes into a shopping list:
 * converts compatible units (e.g. 500g + 1kg = 1.5 kg, 200ml + 300ml = 500 ml)
 * and normalizes ingredient synonyms into clean aisle categories.
 */
object GroceryAggregator {

    private sealed class NormalizedQuantity {
        data class Weight(val grams: Double) : NormalizedQuantity()
        data class Volume(val ml: Double) : NormalizedQuantity()
        data class Count(val count: Double, val unit: String) : NormalizedQuantity()
        data object Raw : NormalizedQuantity()
    }

    private fun normalizeUnit(unit: String?, qty: Double): NormalizedQuantity {
        if (unit.isNullOrBlank()) return NormalizedQuantity.Count(qty, "")
        val u = unit.lowercase().trim().trimEnd('.', ',')
        return when (u) {
            "g", "gr", "grammi", "grammo", "gram", "grams" -> NormalizedQuantity.Weight(qty)
            "kg", "chilo", "chili", "kilogram", "kilograms", "kilo" -> NormalizedQuantity.Weight(qty * 1000.0)
            "oz" -> NormalizedQuantity.Weight(qty * 28.3495)
            "lb", "lbs" -> NormalizedQuantity.Weight(qty * 453.592)
            "ml", "millilitri", "millilitro", "milliliter", "milliliters" -> NormalizedQuantity.Volume(qty)
            "cl" -> NormalizedQuantity.Volume(qty * 10.0)
            "dl" -> NormalizedQuantity.Volume(qty * 100.0)
            "l", "lt", "litro", "litri", "liter", "liters" -> NormalizedQuantity.Volume(qty * 1000.0)
            "cucchiaio", "cucchiai", "tbsp", "tablespoon", "tablespoons" -> NormalizedQuantity.Count(qty, "cucchiai")
            "cucchiaino", "cucchiaini", "tsp", "teaspoon", "teaspoons" -> NormalizedQuantity.Count(qty, "cucchiaini")
            "spicchio", "spicchi", "clove", "cloves" -> NormalizedQuantity.Count(qty, "spicchi")
            "fetta", "fette", "slice", "slices" -> NormalizedQuantity.Count(qty, "fette")
            "uovo", "uova", "egg", "eggs" -> NormalizedQuantity.Count(qty, "uova")
            else -> NormalizedQuantity.Count(qty, unit)
        }
    }

    fun aggregate(recipes: List<RecipeWithDetails>): List<GroceryItem> {
        val sources = recipes.flatMap { details ->
            details.ingredients.map { it to details.recipe.title }
        }
        if (sources.isEmpty()) return emptyList()

        return sources
            .groupBy { (ingredient, _) ->
                PantryMatcher.normalize(ingredient.name).ifBlank { ingredient.name.lowercase().trim() }
            }
            .values
            .map { group ->
                val ingredients = group.map { it.first }
                val titles = group.map { it.second }.distinct()
                val displayName = ingredients.first().name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                val parsedQuantities = ingredients.map { ing ->
                    val num = ing.quantity?.let(Quantities::parse)
                    if (num != null) normalizeUnit(ing.unit, num) else NormalizedQuantity.Raw
                }

                val allWeights = parsedQuantities.all { it is NormalizedQuantity.Weight }
                val allVolumes = parsedQuantities.all { it is NormalizedQuantity.Volume }
                val countUnits = parsedQuantities.mapNotNull { (it as? NormalizedQuantity.Count)?.unit }.distinct()
                val allCompatibleCounts = parsedQuantities.all { it is NormalizedQuantity.Count } && countUnits.size <= 1

                val line: String
                val isMerged: Boolean

                when {
                    allWeights && parsedQuantities.isNotEmpty() -> {
                        val totalGrams = parsedQuantities.filterIsInstance<NormalizedQuantity.Weight>().sumOf { it.grams }
                        val (formattedQty, formattedUnit) = if (totalGrams >= 1000.0 && totalGrams % 100 == 0.0) {
                            Quantities.format(totalGrams / 1000.0) to "kg"
                        } else {
                            Quantities.format(totalGrams) to "g"
                        }
                        line = "$formattedQty $formattedUnit $displayName"
                        isMerged = ingredients.size > 1
                    }
                    allVolumes && parsedQuantities.isNotEmpty() -> {
                        val totalMl = parsedQuantities.filterIsInstance<NormalizedQuantity.Volume>().sumOf { it.ml }
                        val (formattedQty, formattedUnit) = if (totalMl >= 1000.0 && totalMl % 100 == 0.0) {
                            Quantities.format(totalMl / 1000.0) to "l"
                        } else {
                            Quantities.format(totalMl) to "ml"
                        }
                        line = "$formattedQty $formattedUnit $displayName"
                        isMerged = ingredients.size > 1
                    }
                    allCompatibleCounts && parsedQuantities.isNotEmpty() -> {
                        val totalCount = parsedQuantities.filterIsInstance<NormalizedQuantity.Count>().sumOf { it.count }
                        val unit = countUnits.firstOrNull().orEmpty()
                        line = if (unit.isNotBlank()) "${Quantities.format(totalCount)} $unit $displayName" else "${Quantities.format(totalCount)} $displayName"
                        isMerged = ingredients.size > 1
                    }
                    else -> {
                        line = ingredients.mapNotNull { it.rawText ?: it.name }.distinct().joinToString(" + ")
                        isMerged = ingredients.size > 1
                    }
                }

                GroceryItem(
                    name = displayName,
                    line = line,
                    isMerged = isMerged,
                    recipeTitles = titles,
                    category = GroceryCategories.of(displayName),
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}

/** Supermarket aisle bucketing with bilingual keyword dictionaries. */
object GroceryCategories {

    const val PRODUCE = "Produce"
    const val MEAT = "Meat"
    const val SEAFOOD = "Seafood"
    const val EGGS = "Eggs"
    const val DAIRY = "Dairy"
    const val BAKERY = "Bakery"
    const val PANTRY = "Pantry"
    const val CANNED = "Canned"
    const val SPICES = "Spices"
    const val FROZEN = "Frozen"
    const val BEVERAGES = "Beverages"
    const val OTHER = "Other"

    private val BUCKETS: List<Pair<String, List<String>>> = listOf(
        CANNED to listOf(
            "passata", "pelati", "tomato paste", "tomato sauce", "pesto", "broth", "stock",
            "concentrato", "sugo", "ragù", "brodo", "dado", "olive", "sottoli", "sottaceti", "capperi", "canned",
        ),
        SPICES to listOf(
            "olive oil", "extravergine", "extra virgin", "vinegar", "balsamic", "balsamico",
            "oil", "salt", "pepper", "black pepper", "paprika", "cumin", "turmeric",
            "oregano", "cinnamon", "nutmeg", "soy sauce", "mustard", "ketchup", "mayonnaise", "mayo", "vanilla",
            "olio", "aceto", "sale", "pepe", "curcuma",
            "origano", "cannella", "noce moscata", "salsa di soia", "senape", "maionese", "vaniglia",
        ),
        SEAFOOD to listOf(
            "salmon", "tuna", "cod", "prawn", "prawns", "shrimp", "shrimps", "fish", "anchovy", "anchovies",
            "trout", "seabass", "octopus", "squid", "clam", "clams", "mussel", "mussels", "crab", "lobster",
            "salmone", "tonno", "merluzzo", "gambero", "gamberi", "gamberetti", "pesce", "trota", "spigola",
            "branzino", "orata", "polpo", "calamaro", "calamari", "vongola", "vongole", "cozza", "cozze",
            "acciuga", "acciughe", "alice", "alici", "granchio", "aragosta", "seppia", "seppie",
        ),
        MEAT to listOf(
            "chicken", "beef", "pork", "lamb", "bacon", "sausage", "ham", "turkey", "mince", "steak",
            "veal", "duck", "pancetta", "guanciale", "chorizo", "prosciutto",
            "pollo", "manzo", "maiale", "vitello", "agnello", "tacchino", "bistecca", "salsiccia", "salsicce",
            "macinato", "carne", "petto di pollo", "fegato", "coniglio", "costine", "lardo",
        ),
        EGGS to listOf(
            "egg", "eggs", "yolk", "yolks", "egg white", "egg whites",
            "uovo", "uova", "tuorlo", "tuorli", "albume", "albumi",
        ),
        DAIRY to listOf(
            "milk", "cream", "butter", "cheese", "parmesan", "mozzarella", "cheddar", "feta", "ricotta",
            "yogurt", "yoghurt", "mascarpone", "gorgonzola", "pecorino", "brie",
            "latte", "panna", "burro", "formaggio", "formaggi", "parmigiano", "grana", "stracchino",
            "provola", "scamorza", "caciocavallo", "fontina", "taleggio", "burrata",
        ),
        BAKERY to listOf(
            "bread", "sourdough", "baguette", "bun", "tortilla", "pita", "brioche", "croissant", "toast",
            "biscuit", "biscuits", "cookie", "cookies", "cake",
            "pane", "pagnotta", "panino", "panini", "pancarrè", "piadina", "cornetto", "torta", "biscotti",
            "fette biscottate", "savoiardi", "sfoglia", "pasta sfoglia", "pasta frolla",
        ),
        PANTRY to listOf(
            "flour", "sugar", "rice", "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "tagliatelle",
            "noodle", "noodles", "bean", "beans", "lentil", "lentils", "chickpea", "chickpeas", "yeast",
            "baking powder", "cocoa", "chocolate", "almond", "almonds", "walnut", "walnuts", "seed", "seeds",
            "oat", "oats", "couscous", "quinoa", "honey", "syrup",
            "farina", "riso", "risotto", "gnocchi", "fiocchi d'avena", "fagioli", "lenticchie", "ceci",
            "zucchero", "lievito", "amido", "fecola", "cacao", "cioccolato", "mandorle", "noci", "nocciole", "pinoli",
        ),
        FROZEN to listOf("frozen", "ice cream", "surgelato", "surgelati", "congelato", "gelato", "ghiaccio"),
        BEVERAGES to listOf(
            "water", "juice", "wine", "beer", "coffee", "tea", "soda",
            "acqua", "succo", "vino", "birra", "caffè", "tè", "bevanda", "spumante", "prosecco",
        ),
        PRODUCE to listOf(
            "onion", "garlic", "tomato", "potato", "carrot", "celery", "pepper", "chilli", "chili",
            "lettuce", "spinach", "kale", "cucumber", "avocado", "lemon", "lime", "orange", "apple",
            "banana", "berry", "berries", "strawberry", "mushroom", "courgette", "zucchini", "aubergine", "eggplant",
            "broccoli", "cauliflower", "cabbage", "leek", "ginger", "basil", "parsley", "coriander",
            "cilantro", "mint", "thyme", "rosemary", "salad", "peas", "corn", "squash", "pumpkin",
            "cipolla", "cipolle", "aglio", "pomodoro", "pomodori", "pomodorini", "patata", "patate",
            "carota", "carote", "sedano", "peperone", "peperoni", "peperoncino", "lattuga", "insalata",
            "spinaci", "cetriolo", "arancia", "arance", "mela", "mele", "fragola", "fragole",
            "funghi", "fungo", "zucchina", "zucchine", "melanzana", "melanzane", "broccolo", "cavolo",
            "verza", "zenzero", "basilico", "prezzemolo", "rosmarino", "timo", "piselli", "mais",
            "zucca", "porro", "porri", "limone", "limoni",
        ),
    )

    /** Aisle order for the "By Category" view; unknown items sink to the bottom. */
    val ORDER: List<String> = listOf(
        PRODUCE, MEAT, SEAFOOD, EGGS, DAIRY, BAKERY, PANTRY, CANNED, SPICES, FROZEN, BEVERAGES, OTHER
    )

    fun of(ingredientName: String): String {
        val clean = ingredientName.lowercase()
        val tokens = clean.split(Regex("[^a-zA-Z0-9àèéìòùáéíóú]+")).filter { it.isNotBlank() }.toSet()

        for ((category, keywords) in BUCKETS) {
            for (kw in keywords) {
                if (kw.contains(" ")) {
                    if (clean.contains(kw)) return category
                } else {
                    if (tokens.contains(kw)) return category
                }
            }
        }
        return OTHER
    }

    /** String resource for the localized display label of an aisle [category]. */
    fun labelRes(aisle: String): Int = when (aisle) {
        PRODUCE -> R.string.grocery_aisle_produce
        MEAT -> R.string.grocery_aisle_meat
        SEAFOOD -> R.string.grocery_aisle_fish
        EGGS -> R.string.grocery_aisle_eggs
        DAIRY -> R.string.grocery_aisle_dairy
        BAKERY -> R.string.grocery_aisle_bakery
        PANTRY -> R.string.grocery_aisle_pantry
        CANNED -> R.string.grocery_aisle_canned
        SPICES -> R.string.grocery_aisle_spices
        FROZEN -> R.string.grocery_aisle_frozen
        BEVERAGES -> R.string.grocery_aisle_beverages
        else -> R.string.grocery_aisle_other
    }
}
