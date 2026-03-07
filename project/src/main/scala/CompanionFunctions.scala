import scala.meta._

object CompanionFunctions {

  def buildFilterableTerm(columnName: String, termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq($columnName, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(
            $columnName,
            vs.toSeq.map(UnsignedByteArrays.from(_)):_*
          )
      }
    """

  def buildSortableTerm(columnName: String, termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
        def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc($columnName)
        def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc($columnName)
      }
    """

  def buildFilterableNumTerm(columnName: String, termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val $$: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq($columnName, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in($columnName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt($columnName, UnsignedByteArrays.from(v))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte($columnName, UnsignedByteArrays.from(v))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt($columnName, UnsignedByteArrays.from(v))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte($columnName, UnsignedByteArrays.from(v))
      }
    """
}
