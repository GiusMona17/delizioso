package com.delizioso.app.data

import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.R

/** A consolidated grocery-list line. */
data class GroceryItem(
    val name: String,
    /** Final display line, e.g. "3 cups flour" or the original "2 large onions". */
    val line: String,
    /** True when several planned meals were merged into this line. */
    val isMerged: Boolean,
    /** Titles of the planned recipes this line came from (empty for custom items). */
    val recipeTitles: List<String> = emptyList(),
    /** Supermarket aisle, used by the "By Category" view. */
    val category: String = GroceryCategories.OTHER,
)

/**
 * Aggregates ingredients across the planned recipes into a shopping list:
 * quantities that parse as numbers are summed per ingredient name; otherwise the
 * original lines are kept. (No unit conversion — "cups" and "g" are kept separate.)
 */
object GroceryAggregator {

    fun aggregate(recipes: List<RecipeWithDetails>): List<GroceryItem> {
        val sources = recipes.flatMap { details ->
            details.ingredients.map { it to details.recipe.title }
        }
        if (sources.isEmpty()) return emptyList()

        return sources
            .groupBy { (ingredient, _) -> normalize(ingredient.name) }
            .values
            .map { group ->
                val ingredients = group.map { it.first }
                val titles = group.map { it.second }.distinct()
                val name = ingredients.first().name
                val quantities = ingredients.map { it.quantity?.let(Quantities::parse) }
                val allNumeric = quantities.isNotEmpty() && quantities.all { it != null }
                val line = if (allNumeric) {
                    val total = quantities.filterNotNull().sum()
                    val unit = ingredients.firstNotNullOfOrNull { it.unit }
                    buildString {
                        append(Quantities.format(total))
                        if (!unit.isNullOrBlank()) append(" $unit")
                        append(" $name")
                    }
                } else {
                    ingredients.first().rawText ?: name
                }
                GroceryItem(
                    name = name,
                    line = line,
                    isMerged = allNumeric && ingredients.size > 1,
                    recipeTitles = titles,
                    category = GroceryCategories.of(name),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun normalize(name: String): String =
        name.lowercase().trim().trimEnd('.', ',', '!')
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
