package de.ddm

import org.apache.spark.sql.{Dataset, Row, SparkSession}

object Sindy {

  private def readData(input: String, spark: SparkSession): Dataset[Row] = {
    spark
      .read
      .option("inferSchema", "false")
      .option("header", "true")
      .option("quote", "\"")
      .option("delimiter", ";")
      .csv(input)
  }

  def discoverINDs(inputs: List[String], spark: SparkSession): Unit = {
    import spark.implicits._

    val data = inputs.map(input => readData(input, spark))
      .map(table => {
        val cols = table.columns
        table.flatMap(row => for (i <- cols.indices) yield (cols(i), row.getString(i)))
      });
    val mergedData = data.reduce((set1, set2) => set1 union set2);
    val groupedByValue = mergedData.groupByKey(t => t._2)
      .mapGroups((_, iterator) => iterator.map(_._1).toSet);
    val INDs = groupedByValue.flatMap(Set => Set
      .map(currentAttr => (currentAttr, Set.filter(attr => !attr.equals(currentAttr)))))
      .groupByKey(row => row._1)
      .mapGroups((key, iterator) => (key, iterator.map(row => row._2).reduce((set1, set2) => set1.intersect(set2))))
      .collect();

    val sortedINDs = INDs.sortBy(tuple => tuple._1);
    for(tuple <- sortedINDs) {
      val attribute = tuple._1
      val values = tuple._2

      // Check if the set of values is not empty
      if (values.nonEmpty) {
        val valuesString = values.mkString(",");
        println(s"$attribute -> $valuesString")
      }
    }
  }
}
