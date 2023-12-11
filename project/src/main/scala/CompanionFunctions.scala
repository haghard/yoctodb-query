import scala.meta._

object CompanionFunctions {

  def buildFilterableTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val ops: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(
            fieldName,
            vs.toSeq.map(UnsignedByteArrays.from(_)):_*
          )
      }
    """

  def buildSortableTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val ops: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {
        def desc(): com.yandex.yoctodb.query.Order = QueryBuilder.desc(fieldName)
        def asc(): com.yandex.yoctodb.query.Order = QueryBuilder.asc(fieldName)
      }
    """

  def buildFilterableNumTerm(termType: Type.Name, termTypeParamType: Type.Name): Defn.Val =
    q"""
      val ops: ${termType}[${termTypeParamType}] = new ${scala.meta.Init(termType, termType, Seq.empty)}[${termTypeParamType}] {

        def =:=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.eq(fieldName, UnsignedByteArrays.from(v))

        def in(vs: scala.collection.immutable.Set[${termTypeParamType}]): TermCondition =
          QueryBuilder.in(fieldName, vs.toSeq.map(UnsignedByteArrays.from(_)):_*)

        def >(v: ${termTypeParamType}): TermCondition =
         QueryBuilder.gt(fieldName, UnsignedByteArrays.from(v))

        def >=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.gte(fieldName, UnsignedByteArrays.from(v))

        def <(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lt(fieldName, UnsignedByteArrays.from(v))

        def <=(v: ${termTypeParamType}): TermCondition =
          QueryBuilder.lte(fieldName, UnsignedByteArrays.from(v))
      }
    """
}
