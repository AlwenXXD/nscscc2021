package cpu.ifu

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest.FlatSpec

class IfuTest extends FlatSpec with ChiselScalatestTester {
    "Ifu" should "run" in {
      test(new Ifu).withAnnotations(Seq(WriteVcdAnnotation)){
        i=>
          i.io.ex_branch_info_i.valid.poke(false.B)
          i.clock.step(20)
      }
    }
}
