package com.delizioso.app.data.nutrition

/**
 * Per-100 g nutrition for common home-cooking ingredients, in Italian and English.
 *
 * A lookup table rather than an LLM estimate on purpose: a small model produces
 * confident, plausible, unverifiable numbers, and a wrong calorie count that
 * looks right is worse than no number at all. Every figure here is traceable and
 * can be corrected in one place. Values are rounded reference figures for the raw
 * ingredient, which is what a recipe lists.
 */
object NutritionTable {

    /**
     * [gramsPerPiece] lets "2 eggs" or "1 onion" be weighed; [gramsPerMl] converts
     * a volume for ingredients that are not water-dense (oil, honey).
     */
    data class Nutrient(
        val kcal: Double,
        val proteinG: Double,
        val fatG: Double,
        val carbsG: Double,
        val gramsPerPiece: Double? = null,
        val gramsPerMl: Double = 1.0,
    )

    /**
     * Keyed by a substring of the ingredient name. Lookup prefers the longest
     * match, so "burro di arachidi" never resolves as "burro".
     */
    private val ENTRIES: Map<String, Nutrient> = mapOf(
        // --- Meat, fish, eggs ---
        "chicken breast" to Nutrient(165.0, 31.0, 3.6, 0.0),
        "petto di pollo" to Nutrient(165.0, 31.0, 3.6, 0.0),
        "chicken thigh" to Nutrient(209.0, 26.0, 11.0, 0.0),
        "coscia di pollo" to Nutrient(209.0, 26.0, 11.0, 0.0),
        "chicken" to Nutrient(190.0, 27.0, 8.0, 0.0),
        "pollo" to Nutrient(190.0, 27.0, 8.0, 0.0),
        "turkey" to Nutrient(135.0, 29.0, 2.0, 0.0),
        "tacchino" to Nutrient(135.0, 29.0, 2.0, 0.0),
        "ground beef" to Nutrient(250.0, 26.0, 15.0, 0.0),
        "macinato" to Nutrient(250.0, 26.0, 15.0, 0.0),
        "beef" to Nutrient(250.0, 26.0, 15.0, 0.0),
        "manzo" to Nutrient(250.0, 26.0, 15.0, 0.0),
        "pork" to Nutrient(242.0, 27.0, 14.0, 0.0),
        "maiale" to Nutrient(242.0, 27.0, 14.0, 0.0),
        "sausage" to Nutrient(300.0, 18.0, 25.0, 1.0),
        "salsiccia" to Nutrient(300.0, 18.0, 25.0, 1.0),
        "bacon" to Nutrient(400.0, 13.0, 39.0, 1.0),
        "pancetta" to Nutrient(400.0, 13.0, 39.0, 1.0),
        "guanciale" to Nutrient(450.0, 12.0, 45.0, 0.0),
        "prosciutto cotto" to Nutrient(145.0, 18.0, 8.0, 1.0),
        "prosciutto" to Nutrient(195.0, 26.0, 10.0, 0.0),
        "salmon" to Nutrient(208.0, 20.0, 13.0, 0.0),
        "salmone" to Nutrient(208.0, 20.0, 13.0, 0.0),
        "tuna" to Nutrient(130.0, 28.0, 1.0, 0.0),
        "tonno" to Nutrient(130.0, 28.0, 1.0, 0.0),
        "cod" to Nutrient(82.0, 18.0, 0.7, 0.0),
        "merluzzo" to Nutrient(82.0, 18.0, 0.7, 0.0),
        "shrimp" to Nutrient(99.0, 24.0, 0.3, 0.0),
        "gamberi" to Nutrient(99.0, 24.0, 0.3, 0.0),
        "egg" to Nutrient(143.0, 13.0, 9.5, 0.7, gramsPerPiece = 50.0),
        "uova" to Nutrient(143.0, 13.0, 9.5, 0.7, gramsPerPiece = 50.0),
        "uovo" to Nutrient(143.0, 13.0, 9.5, 0.7, gramsPerPiece = 50.0),

        // --- Dairy ---
        "milk" to Nutrient(61.0, 3.2, 3.3, 4.8, gramsPerMl = 1.03),
        "latte di cocco" to Nutrient(230.0, 2.3, 24.0, 6.0),
        "coconut milk" to Nutrient(230.0, 2.3, 24.0, 6.0),
        "latte" to Nutrient(61.0, 3.2, 3.3, 4.8, gramsPerMl = 1.03),
        "butter" to Nutrient(717.0, 0.9, 81.0, 0.1),
        "burro di arachidi" to Nutrient(588.0, 25.0, 50.0, 20.0),
        "peanut butter" to Nutrient(588.0, 25.0, 50.0, 20.0),
        "burro" to Nutrient(717.0, 0.9, 81.0, 0.1),
        "cream" to Nutrient(340.0, 2.0, 36.0, 3.0),
        "panna" to Nutrient(340.0, 2.0, 36.0, 3.0),
        "parmesan" to Nutrient(392.0, 36.0, 25.0, 3.2),
        "parmigiano" to Nutrient(392.0, 36.0, 25.0, 3.2),
        "pecorino" to Nutrient(387.0, 26.0, 31.0, 0.0),
        "mozzarella" to Nutrient(280.0, 22.0, 22.0, 2.2),
        "ricotta" to Nutrient(174.0, 11.0, 13.0, 3.0),
        "mascarpone" to Nutrient(430.0, 4.0, 44.0, 4.0),
        "greek yogurt" to Nutrient(59.0, 10.0, 0.4, 3.6),
        "yogurt greco" to Nutrient(59.0, 10.0, 0.4, 3.6),
        "yogurt" to Nutrient(61.0, 3.5, 3.3, 4.7),
        "cheese" to Nutrient(350.0, 25.0, 27.0, 2.0),
        "formaggio" to Nutrient(350.0, 25.0, 27.0, 2.0),

        // --- Grains and flour (dry weight, as a recipe lists them) ---
        "pasta" to Nutrient(371.0, 13.0, 1.5, 75.0),
        "spaghetti" to Nutrient(371.0, 13.0, 1.5, 75.0),
        "noodles" to Nutrient(350.0, 12.0, 1.0, 71.0),
        "udon" to Nutrient(350.0, 12.0, 1.0, 71.0),
        "rice" to Nutrient(360.0, 7.0, 0.6, 79.0),
        "riso" to Nutrient(360.0, 7.0, 0.6, 79.0),
        "flour" to Nutrient(364.0, 10.0, 1.0, 76.0),
        "farina" to Nutrient(364.0, 10.0, 1.0, 76.0),
        "bread" to Nutrient(265.0, 9.0, 3.2, 49.0),
        "pane" to Nutrient(265.0, 9.0, 3.2, 49.0),
        "breadcrumbs" to Nutrient(395.0, 13.0, 5.0, 72.0),
        "pangrattato" to Nutrient(395.0, 13.0, 5.0, 72.0),
        "oats" to Nutrient(389.0, 17.0, 7.0, 66.0),
        "avena" to Nutrient(389.0, 17.0, 7.0, 66.0),
        "couscous" to Nutrient(376.0, 13.0, 0.6, 77.0),
        "polenta" to Nutrient(370.0, 8.0, 1.8, 79.0),
        "tortilla" to Nutrient(310.0, 8.0, 8.0, 50.0),

        // --- Legumes ---
        "lentils" to Nutrient(353.0, 25.0, 1.0, 60.0),
        "lenticchie" to Nutrient(353.0, 25.0, 1.0, 60.0),
        "chickpeas" to Nutrient(364.0, 19.0, 6.0, 61.0),
        "ceci" to Nutrient(364.0, 19.0, 6.0, 61.0),
        "beans" to Nutrient(333.0, 21.0, 1.0, 60.0),
        "fagioli" to Nutrient(333.0, 21.0, 1.0, 60.0),
        "peas" to Nutrient(81.0, 5.0, 0.4, 14.0),
        "piselli" to Nutrient(81.0, 5.0, 0.4, 14.0),
        "tofu" to Nutrient(76.0, 8.0, 4.8, 1.9),

        // --- Vegetables ---
        "potato" to Nutrient(77.0, 2.0, 0.1, 17.0, gramsPerPiece = 150.0),
        "patate" to Nutrient(77.0, 2.0, 0.1, 17.0, gramsPerPiece = 150.0),
        "patata" to Nutrient(77.0, 2.0, 0.1, 17.0, gramsPerPiece = 150.0),
        "tomato" to Nutrient(18.0, 0.9, 0.2, 3.9, gramsPerPiece = 120.0),
        "pomodor" to Nutrient(18.0, 0.9, 0.2, 3.9, gramsPerPiece = 120.0),
        "onion" to Nutrient(40.0, 1.1, 0.1, 9.0, gramsPerPiece = 110.0),
        "cipolla" to Nutrient(40.0, 1.1, 0.1, 9.0, gramsPerPiece = 110.0),
        "garlic" to Nutrient(149.0, 6.0, 0.5, 33.0, gramsPerPiece = 3.0),
        "aglio" to Nutrient(149.0, 6.0, 0.5, 33.0, gramsPerPiece = 3.0),
        "carrot" to Nutrient(41.0, 0.9, 0.2, 10.0, gramsPerPiece = 60.0),
        "carota" to Nutrient(41.0, 0.9, 0.2, 10.0, gramsPerPiece = 60.0),
        "zucchini" to Nutrient(17.0, 1.2, 0.3, 3.1, gramsPerPiece = 200.0),
        "zucchin" to Nutrient(17.0, 1.2, 0.3, 3.1, gramsPerPiece = 200.0),
        "eggplant" to Nutrient(25.0, 1.0, 0.2, 6.0, gramsPerPiece = 250.0),
        "melanzan" to Nutrient(25.0, 1.0, 0.2, 6.0, gramsPerPiece = 250.0),
        "bell pepper" to Nutrient(26.0, 1.0, 0.3, 6.0, gramsPerPiece = 150.0),
        "peperone" to Nutrient(26.0, 1.0, 0.3, 6.0, gramsPerPiece = 150.0),
        "spinach" to Nutrient(23.0, 2.9, 0.4, 3.6),
        "spinaci" to Nutrient(23.0, 2.9, 0.4, 3.6),
        "mushroom" to Nutrient(22.0, 3.1, 0.3, 3.3),
        "funghi" to Nutrient(22.0, 3.1, 0.3, 3.3),
        "broccoli" to Nutrient(34.0, 2.8, 0.4, 7.0),
        "celery" to Nutrient(16.0, 0.7, 0.2, 3.0),
        "sedano" to Nutrient(16.0, 0.7, 0.2, 3.0),
        "lettuce" to Nutrient(15.0, 1.4, 0.2, 2.9),
        "lattuga" to Nutrient(15.0, 1.4, 0.2, 2.9),
        "cucumber" to Nutrient(15.0, 0.7, 0.1, 3.6),
        "cetriolo" to Nutrient(15.0, 0.7, 0.1, 3.6),
        "cabbage" to Nutrient(25.0, 1.3, 0.1, 6.0),
        "cavolo" to Nutrient(25.0, 1.3, 0.1, 6.0),
        "corn" to Nutrient(86.0, 3.2, 1.2, 19.0),
        "mais" to Nutrient(86.0, 3.2, 1.2, 19.0),

        // --- Fruit ---
        "apple" to Nutrient(52.0, 0.3, 0.2, 14.0, gramsPerPiece = 180.0),
        "mela" to Nutrient(52.0, 0.3, 0.2, 14.0, gramsPerPiece = 180.0),
        "banana" to Nutrient(89.0, 1.1, 0.3, 23.0, gramsPerPiece = 120.0),
        "lemon" to Nutrient(29.0, 1.1, 0.3, 9.0, gramsPerPiece = 100.0),
        "limone" to Nutrient(29.0, 1.1, 0.3, 9.0, gramsPerPiece = 100.0),
        "lime" to Nutrient(30.0, 0.7, 0.2, 11.0, gramsPerPiece = 70.0),
        "orange" to Nutrient(47.0, 0.9, 0.1, 12.0, gramsPerPiece = 150.0),
        "arancia" to Nutrient(47.0, 0.9, 0.1, 12.0, gramsPerPiece = 150.0),
        "strawberr" to Nutrient(32.0, 0.7, 0.3, 8.0),
        "fragole" to Nutrient(32.0, 0.7, 0.3, 8.0),
        "avocado" to Nutrient(160.0, 2.0, 15.0, 9.0, gramsPerPiece = 200.0),

        // --- Fats ---
        "olive oil" to Nutrient(884.0, 0.0, 100.0, 0.0, gramsPerMl = 0.92),
        "olio" to Nutrient(884.0, 0.0, 100.0, 0.0, gramsPerMl = 0.92),
        "oil" to Nutrient(884.0, 0.0, 100.0, 0.0, gramsPerMl = 0.92),

        // --- Sugars and sweets ---
        "brown sugar" to Nutrient(380.0, 0.0, 0.0, 98.0),
        "zucchero di canna" to Nutrient(380.0, 0.0, 0.0, 98.0),
        "sugar" to Nutrient(387.0, 0.0, 0.0, 100.0),
        "zucchero" to Nutrient(387.0, 0.0, 0.0, 100.0),
        "honey" to Nutrient(304.0, 0.3, 0.0, 82.0, gramsPerMl = 1.42),
        "miele" to Nutrient(304.0, 0.3, 0.0, 82.0, gramsPerMl = 1.42),
        "maple syrup" to Nutrient(260.0, 0.0, 0.0, 67.0, gramsPerMl = 1.32),
        "chocolate" to Nutrient(546.0, 4.9, 31.0, 61.0),
        "cioccolato" to Nutrient(546.0, 4.9, 31.0, 61.0),
        "cocoa" to Nutrient(228.0, 20.0, 14.0, 58.0),
        "cacao" to Nutrient(228.0, 20.0, 14.0, 58.0),

        // --- Nuts ---
        "almond" to Nutrient(579.0, 21.0, 50.0, 22.0),
        "mandorle" to Nutrient(579.0, 21.0, 50.0, 22.0),
        "walnut" to Nutrient(654.0, 15.0, 65.0, 14.0),
        "noci" to Nutrient(654.0, 15.0, 65.0, 14.0),
        "pine nuts" to Nutrient(673.0, 14.0, 68.0, 13.0),
        "pinoli" to Nutrient(673.0, 14.0, 68.0, 13.0),

        // --- Condiments and liquids ---
        "soy sauce" to Nutrient(53.0, 8.0, 0.0, 5.0, gramsPerMl = 1.1),
        "salsa di soia" to Nutrient(53.0, 8.0, 0.0, 5.0, gramsPerMl = 1.1),
        "tomato paste" to Nutrient(82.0, 4.3, 0.5, 19.0),
        "concentrato di pomodoro" to Nutrient(82.0, 4.3, 0.5, 19.0),
        "passata" to Nutrient(32.0, 1.3, 0.2, 7.0, gramsPerMl = 1.05),
        "tomato sauce" to Nutrient(32.0, 1.3, 0.2, 7.0, gramsPerMl = 1.05),
        "mayonnaise" to Nutrient(680.0, 1.0, 75.0, 1.0),
        "maionese" to Nutrient(680.0, 1.0, 75.0, 1.0),
        "mustard" to Nutrient(66.0, 4.0, 4.0, 6.0),
        "senape" to Nutrient(66.0, 4.0, 4.0, 6.0),
        "ketchup" to Nutrient(101.0, 1.0, 0.1, 25.0),
        "vinegar" to Nutrient(20.0, 0.0, 0.0, 0.9),
        "aceto" to Nutrient(20.0, 0.0, 0.0, 0.9),
        "broth" to Nutrient(7.0, 0.5, 0.2, 0.7),
        "stock" to Nutrient(7.0, 0.5, 0.2, 0.7),
        "brodo" to Nutrient(7.0, 0.5, 0.2, 0.7),
        "wine" to Nutrient(83.0, 0.1, 0.0, 2.6, gramsPerMl = 0.99),
        "vino" to Nutrient(83.0, 0.1, 0.0, 2.6, gramsPerMl = 0.99),

        // --- Aromatics and seasonings (near-zero, but they must count as matched
        // so a recipe full of herbs doesn't look like a coverage failure) ---
        "water" to Nutrient(0.0, 0.0, 0.0, 0.0),
        "acqua" to Nutrient(0.0, 0.0, 0.0, 0.0),
        "salt" to Nutrient(0.0, 0.0, 0.0, 0.0),
        "sale" to Nutrient(0.0, 0.0, 0.0, 0.0),
        "black pepper" to Nutrient(251.0, 10.0, 3.0, 64.0),
        "pepe" to Nutrient(251.0, 10.0, 3.0, 64.0),
        "ginger" to Nutrient(80.0, 1.8, 0.8, 18.0),
        "zenzero" to Nutrient(80.0, 1.8, 0.8, 18.0),
        "basil" to Nutrient(23.0, 3.0, 0.6, 2.6),
        "basilico" to Nutrient(23.0, 3.0, 0.6, 2.6),
        "parsley" to Nutrient(36.0, 3.0, 0.8, 6.0),
        "prezzemolo" to Nutrient(36.0, 3.0, 0.8, 6.0),
        "yeast" to Nutrient(325.0, 40.0, 7.0, 41.0),
        "lievito" to Nutrient(325.0, 40.0, 7.0, 41.0),
    )

    /** Longest key first, so a specific entry always beats a generic one. */
    private val BY_LENGTH = ENTRIES.entries.sortedByDescending { it.key.length }

    /** The table entry for an ingredient name, or null when it isn't known. */
    fun lookup(ingredientName: String): Nutrient? {
        val name = ingredientName.lowercase()
        return BY_LENGTH.firstOrNull { name.contains(it.key) }?.value
    }
}
