package ng.pixelslimited.health.data

/** Typical reference values per 100 g. Estimates, not lab figures. */
class Food(val name: String, val kcal: Int, val carbs: Double, val protein: Double, val fat: Double, val portion: Int, val keys: List<String> = emptyList())

object Foods {
    val all = listOf(
        Food("Apple", 52, 14.0, 0.3, 0.2, 180, listOf("apple")),
        Food("Banana", 89, 23.0, 1.1, 0.3, 120, listOf("banana")),
        Food("Orange", 47, 12.0, 0.9, 0.1, 150, listOf("orange", "tangerine", "mandarin", "clementine")),
        Food("Mango", 60, 15.0, 0.8, 0.4, 200, listOf("mango")),
        Food("Pineapple", 50, 13.0, 0.5, 0.1, 150, listOf("pineapple")),
        Food("Watermelon", 30, 8.0, 0.6, 0.2, 300, listOf("watermelon")),
        Food("Grapes", 69, 18.0, 0.7, 0.2, 100, listOf("grape")),
        Food("Strawberries", 32, 8.0, 0.7, 0.3, 100, listOf("strawberr")),
        Food("Pawpaw (papaya)", 43, 11.0, 0.5, 0.3, 200, listOf("papaya", "pawpaw")),
        Food("Avocado", 160, 9.0, 2.0, 15.0, 100, listOf("avocado")),
        Food("Coconut", 354, 15.0, 3.3, 33.0, 45, listOf("coconut")),
        Food("Tomato", 18, 3.9, 0.9, 0.2, 120, listOf("tomato")),
        Food("Carrot", 41, 10.0, 0.9, 0.2, 80, listOf("carrot")),
        Food("Cucumber", 15, 3.6, 0.7, 0.1, 100, listOf("cucumber")),
        Food("Salad", 20, 3.5, 1.5, 0.2, 150, listOf("salad")),
        Food("Potato, boiled", 87, 20.0, 1.9, 0.1, 150, listOf("potato")),
        Food("Sweet potato", 86, 20.0, 1.6, 0.1, 150, listOf("sweet potato")),
        Food("Yam, boiled", 118, 28.0, 1.5, 0.2, 200, listOf("yam")),
        Food("Pounded yam", 118, 28.0, 1.5, 0.2, 250),
        Food("Plantain, boiled", 122, 32.0, 1.3, 0.4, 150, listOf("plantain")),
        Food("Plantain, fried", 260, 36.0, 1.5, 13.0, 100),
        Food("Rice, white, cooked", 130, 28.0, 2.7, 0.3, 200, listOf("rice")),
        Food("Jollof rice", 160, 27.0, 3.0, 4.0, 250, listOf("jollof")),
        Food("Fried rice", 163, 25.0, 4.0, 5.0, 250, listOf("fried rice")),
        Food("Beans, cooked", 127, 23.0, 8.7, 0.5, 200, listOf("bean", "legume")),
        Food("Moi moi", 140, 14.0, 9.0, 5.0, 150),
        Food("Akara", 250, 20.0, 9.0, 15.0, 100),
        Food("Eba", 150, 36.0, 0.6, 0.2, 250),
        Food("Fufu / tuwo", 140, 33.0, 1.0, 0.3, 250, listOf("fufu")),
        Food("Egusi soup", 190, 4.0, 8.0, 16.0, 200),
        Food("Okra soup", 70, 6.0, 3.0, 4.0, 200, listOf("okra")),
        Food("Bread", 265, 49.0, 9.0, 3.2, 60, listOf("bread", "baguette", "bun", "loaf", "toast")),
        Food("Egg, boiled", 155, 1.1, 13.0, 11.0, 50, listOf("egg")),
        Food("Chicken, grilled", 165, 0.0, 31.0, 3.6, 150, listOf("chicken")),
        Food("Chicken, fried", 290, 10.0, 24.0, 17.0, 150, listOf("fried chicken")),
        Food("Suya (beef)", 250, 3.0, 26.0, 15.0, 100),
        Food("Fish, grilled", 130, 0.0, 24.0, 3.0, 150, listOf("fish", "salmon", "tilapia", "seafood")),
        Food("Pizza", 266, 33.0, 11.0, 10.0, 110, listOf("pizza")),
        Food("Hamburger", 250, 30.0, 13.0, 9.0, 150, listOf("hamburger", "burger")),
        Food("Sandwich", 250, 30.0, 12.0, 9.0, 150, listOf("sandwich")),
        Food("French fries", 312, 41.0, 3.4, 15.0, 120, listOf("french fries", "fries")),
        Food("Pasta, cooked", 158, 31.0, 5.8, 0.9, 200, listOf("pasta", "spaghetti", "macaroni")),
        Food("Noodles, cooked", 140, 20.0, 3.5, 5.0, 200, listOf("noodle")),
        Food("Cake", 370, 53.0, 5.0, 16.0, 80, listOf("cake", "cupcake", "pastry")),
        Food("Doughnut", 421, 51.0, 5.0, 22.0, 60, listOf("doughnut", "donut")),
        Food("Ice cream", 207, 24.0, 3.5, 11.0, 100, listOf("ice cream")),
        Food("Groundnuts", 567, 16.0, 26.0, 49.0, 30, listOf("peanut", "groundnut")),
        Food("Milk", 61, 4.8, 3.2, 3.3, 250, listOf("milk")),
        Food("Orange juice", 45, 10.0, 0.7, 0.2, 250, listOf("juice")),
        Food("Soft drink", 41, 10.6, 0.0, 0.0, 330, listOf("soda", "cola", "soft drink")),
    )

    /** Foods that fit the photo's labels, best label first. */
    fun match(labels: List<String>): List<Food> {
        val low = labels.map { it.lowercase() }
        return all.mapNotNull { f ->
            val at = low.indexOfFirst { l -> f.keys.any { k -> l.contains(k) } }
            if (at >= 0) at to f else null
        }.sortedBy { it.first }.map { it.second }
    }
}
