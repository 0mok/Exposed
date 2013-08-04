fun<T> Iterable<T>.single() : T {
    var answer: T? = null;
    var found: Boolean = false;
    for (t in this) {
        if (found) throw RuntimeException ("Duplicate items")

        answer = t;
        found = true;
    }

    if (!found) throw RuntimeException ("No items found")
    return answer!!;
}

fun<T> Iterable<T>.any() : Boolean {
    for (t in this) {
        return true
    }
    return false
}

fun<T> Iterable<T>.firstOrDefault() : T? {
    for (t in this) {
        return t
    }
    return null
}
