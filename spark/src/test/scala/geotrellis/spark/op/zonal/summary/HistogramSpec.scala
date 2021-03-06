package geotrellis.spark.op.zonal.summary

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.testfiles._

import geotrellis.vector._

import org.scalatest.FunSpec

import collection.immutable.HashMap

class HistogramSpec extends FunSpec
    with TestEnvironment
    with TestFiles
    with RasterRDDMatchers
    with OnlyIfCanRunSpark {

  describe("Histogram Zonal Summary Operation") {

    ifCanRunSpark {

      val modHundred = Mod10000TestFile
      val ones = AllOnesTestFile

      val tileLayout = modHundred.metaData.tileLayout
      val count = (modHundred.count * tileLayout.tileCols * tileLayout.tileRows).toInt
      val totalExtent = modHundred.metaData.extent

      it("should get correct histogram over whole raster extent") {
        val histogram = modHundred.zonalHistogram(totalExtent.toPolygon)

        var map = HashMap[Int, Int]()

        for (i <- 0 until count) {
          val key = i % 10000
          val v = map.getOrElse(key, 0) + 1
          map = map + (key -> v)
        }

        map.foreach { case (v, k) => histogram.getItemCount(v) should be (k) }
      }

      it("should get correct histogram over a quarter of the extent") {
        val xd = totalExtent.xmax - totalExtent.xmin
        val yd = totalExtent.ymax - totalExtent.ymin

        val quarterExtent = Extent(
          totalExtent.xmin,
          totalExtent.ymin,
          totalExtent.xmin + xd / 2,
          totalExtent.ymin + yd / 2
        )

        val histogram = ones.zonalHistogram(quarterExtent.toPolygon)

        histogram.getMinMaxValues should be (1, 1)
        histogram.getItemCount(1) should be (count / 4)
      }

      it("should get correct histogram over half of the extent in diamond shape") {
        val xd = totalExtent.xmax - totalExtent.xmin
        val yd = totalExtent.ymax - totalExtent.ymin


        val p1 = Point(totalExtent.xmin + xd / 2, totalExtent.ymax)
        val p2 = Point(totalExtent.xmax, totalExtent.ymin + yd / 2)
        val p3 = Point(totalExtent.xmin + xd / 2, totalExtent.ymin)
        val p4 = Point(totalExtent.xmin, totalExtent.ymin + yd / 2)

        val poly = Polygon(Line(Array(p1, p2, p3, p4, p1)))

        val histogram = ones.zonalHistogram(poly)

        histogram.getMinMaxValues should be (1, 1)
        histogram.getItemCount(1) should be (count / 2)
      }

      it("should get correct histogram over polygon with hole") {
        val xd = totalExtent.xmax - totalExtent.xmin
        val yd = totalExtent.ymax - totalExtent.ymin


        val pe1 = Point(totalExtent.xmin + xd / 2, totalExtent.ymax)
        val pe2 = Point(totalExtent.xmax, totalExtent.ymin + yd / 2)
        val pe3 = Point(totalExtent.xmin + xd / 2, totalExtent.ymin)
        val pe4 = Point(totalExtent.xmin, totalExtent.ymin + yd / 2)

        val exterior = Line(Array(pe1, pe2, pe3, pe4, pe1))

        val pi1 = Point(totalExtent.xmin + xd / 2, totalExtent.ymax - yd / 4)
        val pi2 = Point(totalExtent.xmax - xd / 4, totalExtent.ymin + yd / 2)
        val pi3 = Point(totalExtent.xmin + xd / 2, totalExtent.ymin + yd / 4)
        val pi4 = Point(totalExtent.xmin + xd / 4, totalExtent.ymin + yd / 2)

        val interior = Line(Array(pi1, pi2, pi3, pi4, pi1))

        val poly = Polygon(exterior, interior)
        val withoutHoleArea = Polygon(exterior).area
        val area = poly.area
        val res = ((count / 2) * (area / withoutHoleArea)).round.toInt

        val histogram = ones.zonalHistogram(poly)

        histogram.getMinMaxValues should be (1, 1)
        histogram.getItemCount(1) should be (res)
      }
    }
  }

}
