package org.opencv.core

import org.opencv.core.Mat.*
import java.lang.RuntimeException

fun Mat.get(row: Int, col: Int, data: ByteArray)  = this.get(row, col, data)
fun Mat.get(indices: IntArray, data: ByteArray)  = this.get(indices, data)
fun Mat.put(row: Int, col: Int, data: ByteArray)  = this.put(row, col, data)
fun Mat.put(indices: IntArray, data: ByteArray)  = this.put(indices, data)

fun Mat.get(row: Int, col: Int, data: ShortArray)  = this.get(row, col, data)
fun Mat.get(indices: IntArray, data: ShortArray)  = this.get(indices, data)
fun Mat.put(row: Int, col: Int, data: ShortArray)  = this.put(row, col, data)
fun Mat.put(indices: IntArray, data: ShortArray)  = this.put(indices, data)

/***
 *  Example use:
 *
 *  val (b, g, r) = mat.at<Byte>(50, 50).v3c
 *  mat.at<Byte>(50, 50).`val` = T3(120, 113, 34)
 *
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Mat.at(row: Int, col: Int) : Atable<T> =
    when (T::class) {
        Byte::class, Double::class, Float::class, Int::class, Short::class -> this.at(
            T::class.java,
            row,
            col
        )
        else -> throw RuntimeException("Unsupported class type")
    }

@Suppress("UNCHECKED_CAST")
inline fun <reified T> Mat.at(idx: IntArray) : Atable<T> =
    when (T::class) {
        Byte::class, Double::class, Float::class, Int::class, Short::class -> this.at(
            T::class.java,
            idx
        )
        else -> throw RuntimeException("Unsupported class type")
    }

class AtableByte(val mat: Mat, val indices: IntArray): Atable<Byte> {

    constructor(mat: Mat, row: Int, col: Int) : this(mat, intArrayOf(row, col))

    override fun getV(): Byte {
        val data = ByteArray(1)
        mat.get(indices, data)
        return data[0]
    }

    override fun setV(v: Byte) {
        val data = byteArrayOf(v)
        mat.put(indices, data)
    }

    override fun getV2c(): Tuple2<Byte> {
        val data = ByteArray(2)
        mat.get(indices, data)
        return Tuple2(data[0], data[1])
    }

    override fun setV2c(v: Tuple2<Byte>) {
        val data = byteArrayOf(v._0, v._1)
        mat.put(indices, data)
    }

    override fun getV3c(): Tuple3<Byte> {
        val data = ByteArray(3)
        mat.get(indices, data)
        return Tuple3(data[0], data[1], data[2])
    }

    override fun setV3c(v: Tuple3<Byte>) {
        val data = byteArrayOf(v._0, v._1, v._2)
        mat.put(indices, data)
    }

    override fun getV4c(): Tuple4<Byte> {
        val data = ByteArray(4)
        mat.get(indices, data)
        return Tuple4(data[0], data[1], data[2], data[3])
    }

    override fun setV4c(v: Tuple4<Byte>) {
        val data = byteArrayOf(v._0, v._1, v._2, v._3)
        mat.put(indices, data)
    }
}

class AtableShort(val mat: Mat, val indices: IntArray): Atable<Short> {

    constructor(mat: Mat, row: Int, col: Int) : this(mat, intArrayOf(row, col))

    override fun getV(): Short {
        val data = ShortArray(1)
        mat.get(indices, data)
        return data[0]
    }

    override fun setV(v: Short) {
        val data = shortArrayOf(v)
        mat.put(indices, data)
    }

    override fun getV2c(): Tuple2<Short> {
        val data = ShortArray(2)
        mat.get(indices, data)
        return Tuple2(data[0], data[1])
    }

    override fun setV2c(v: Tuple2<Short>) {
        val data = shortArrayOf(v._0, v._1)
        mat.put(indices, data)
    }

    override fun getV3c(): Tuple3<Short> {
        val data = ShortArray(3)
        mat.get(indices, data)
        return Tuple3(data[0], data[1], data[2])
    }

    override fun setV3c(v: Tuple3<Short>) {
        val data = shortArrayOf(v._0, v._1, v._2)
        mat.put(indices, data)
    }

    override fun getV4c(): Tuple4<Short> {
        val data = ShortArray(4)
        mat.get(indices, data)
        return Tuple4(data[0], data[1], data[2], data[3])
    }

    override fun setV4c(v: Tuple4<Short>) {
        val data = shortArrayOf(v._0, v._1, v._2, v._3)
        mat.put(indices, data)
    }
}

operator fun <T> Tuple2<T>.component1(): T = this._0
operator fun <T> Tuple2<T>.component2(): T = this._1

operator fun <T> Tuple3<T>.component1(): T = this._0
operator fun <T> Tuple3<T>.component2(): T = this._1
operator fun <T> Tuple3<T>.component3(): T = this._2

operator fun <T> Tuple4<T>.component1(): T = this._0
operator fun <T> Tuple4<T>.component2(): T = this._1
operator fun <T> Tuple4<T>.component3(): T = this._2
operator fun <T> Tuple4<T>.component4(): T = this._3

fun <T> T2(_0: T, _1: T) : Tuple2<T> = Tuple2(_0, _1)
fun <T> T3(_0: T, _1: T, _2: T) : Tuple3<T> = Tuple3(_0, _1, _2)
fun <T> T4(_0: T, _1: T, _2: T, _3: T) : Tuple4<T> = Tuple4(_0, _1, _2, _3)
