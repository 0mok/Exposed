package org.jetbrains.exposed.sql

import java.lang.IllegalStateException

interface SizedIterable<out T>: Iterable<T> {
    fun limit(n: Int, offset: Int = 0): SizedIterable<T>
    fun count(): Int
    fun empty(): Boolean
    fun forUpdate(): SizedIterable<T> = this
    fun notForUpdate(): SizedIterable<T> = this
    fun copy() : SizedIterable<T>
    fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) : SizedIterable<T>
}

fun <T> emptySized() : SizedIterable<T> = EmptySizedIterable()

class EmptySizedIterable<out T> : SizedIterable<T>, Iterator<T> {
    override fun count(): Int = 0

    override fun limit(n: Int, offset: Int): SizedIterable<T> = this

    override fun empty(): Boolean = true

    override operator fun iterator(): Iterator<T> = this

    override operator fun next(): T {
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean = false

    override fun copy(): SizedIterable<T> = this

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this
}

class SizedCollection<out T>(val delegate: Collection<T>): SizedIterable<T> {
    constructor(value: T) : this(listOf(value))
    override fun limit(n: Int, offset: Int): SizedIterable<T> = SizedCollection(delegate.drop(offset).take(n))

    override operator fun iterator() = delegate.iterator()
    override fun count() = delegate.size
    override fun empty() = delegate.isEmpty()
    override fun copy(): SizedIterable<T> = SizedCollection(delegate)
    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T> = this
}

class LazySizedCollection<out T>(val delegate: SizedIterable<T>): SizedIterable<T> {
    private var _wrapper: List<T>? = null
    private var _size: Int? = null
    private var _empty: Boolean? = null

    val wrapper: List<T> get() {
        if (_wrapper == null) {
            _wrapper = delegate.toList()
        }
        return _wrapper!!
    }

    override fun limit(n: Int, offset: Int): SizedIterable<T> = copy().limit(n, offset)
    override operator fun iterator() = wrapper.iterator()
    override fun count() = _wrapper?.size ?: _count()
    override fun empty() = _wrapper?.isEmpty() ?: _empty()
    override fun forUpdate(): SizedIterable<T> {
        if (_wrapper != null && delegate is Query && !delegate.isForUpdate()) {
            throw IllegalStateException("Impossible to change forUpdate state for loaded data")
        }
        return delegate.forUpdate()
    }
    override fun notForUpdate(): SizedIterable<T> {
        if (_wrapper != null && delegate is Query && delegate.isForUpdate()) {
            throw IllegalStateException("Impossible to change forUpdate state for loaded data")
        }
        return delegate.notForUpdate()
    }

    private fun _count(): Int {
        if (_size == null) {
            _size = delegate.count()
            _empty = (_size == 0)
        }
        return _size!!
    }

    private fun _empty(): Boolean {
        if (_empty == null) {
            _empty = delegate.empty()
            if (_empty == true) _size = 0
        }

        return _empty!!
    }

    override fun copy(): SizedIterable<T> = LazySizedCollection(delegate.copy())

    override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>): SizedIterable<T>
            = LazySizedCollection(delegate.orderBy(*order))
}

infix fun <T, R> SizedIterable<T>.mapLazy(f:(T)->R):SizedIterable<R> {
    val source = this
    return object : SizedIterable<R> {
        override fun limit(n: Int, offset: Int): SizedIterable<R> = source.copy().limit(n, offset).mapLazy(f)
        override fun forUpdate(): SizedIterable<R> = source.copy().forUpdate().mapLazy(f)
        override fun notForUpdate(): SizedIterable<R> = source.copy().notForUpdate().mapLazy(f)
        override fun count(): Int = source.count()
        override fun empty(): Boolean = source.empty()
        override fun copy(): SizedIterable<R> = source.copy().mapLazy(f)
        override fun orderBy(vararg order: Pair<Expression<*>, SortOrder>) = source.orderBy(*order).mapLazy(f)

        override operator fun iterator(): Iterator<R> {
            val sourceIterator = source.iterator()
            return object: Iterator<R> {
                override operator fun next(): R = f(sourceIterator.next())

                override fun hasNext(): Boolean = sourceIterator.hasNext()
            }

        }
    }
}
