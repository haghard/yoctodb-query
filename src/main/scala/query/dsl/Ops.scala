package query.dsl

sealed trait Ops[T]

trait Filterable[T] extends Ops[T] {
  def =:=(v: T): com.yandex.yoctodb.query.TermCondition

  def not_=:=(v: T): com.yandex.yoctodb.query.Condition =
    com.yandex.yoctodb.query.QueryBuilder.not(=:=(v))

  def in(vs: scala.collection.immutable.Set[T]): com.yandex.yoctodb.query.TermCondition

}

trait FilterableNum[T] extends Filterable[T] {
  def >(v: T): com.yandex.yoctodb.query.TermCondition

  def >=(v: T): com.yandex.yoctodb.query.TermCondition

  def <(v: T): com.yandex.yoctodb.query.TermCondition

  def <=(v: T): com.yandex.yoctodb.query.TermCondition

}

trait Sortable[T] extends Ops[T] {
  def desc(): com.yandex.yoctodb.query.Order
  def asc(): com.yandex.yoctodb.query.Order

}

trait Both[T] extends Filterable[T] with Sortable[T]

trait BothNum[T] extends FilterableNum[T] with Sortable[T]

trait DoubleWriter {
  protected def writeDouble(d: Double): Array[Byte] =
    writeLong(java.lang.Double.doubleToRawLongBits(d))

  private def writeLong(i: Long): Array[Byte] = {
    val array = Array.ofDim[Byte](java.lang.Long.BYTES)
    array(0) = (i >>> 56).toByte
    array(1) = (i >>> 48).toByte
    array(2) = (i >>> 40).toByte
    array(3) = (i >>> 32).toByte
    array(4) = (i >>> 24).toByte
    array(5) = (i >>> 16).toByte
    array(6) = (i >>> 8).toByte
    array(7) = i.toByte
    array
  }

}

trait IndexColumn[A] {
  def name: String
  def $ : Ops[A]

}
