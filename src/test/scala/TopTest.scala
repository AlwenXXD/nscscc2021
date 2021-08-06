import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class TopTest extends FlatSpec with ChiselScalatestTester {
  (new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Top)))
  "Top" should "run" in {
    test(new Top).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>i.clock.step(10)
    }
  }
}
