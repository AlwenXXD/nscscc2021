package cpu.ifu

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation
import org.scalatest.FlatSpec

class BpuTest extends FlatSpec with ChiselScalatestTester {
  "Bpu" should "give right information" in {
    test(new BPU).withAnnotations(Seq(WriteVcdAnnotation)) {
      i =>
        i.io.inst_packet_i.bits.data(0).poke("h01294820".U)
        i.io.inst_packet_i.bits.data(1).poke("h112afffe".U)
        i.io.inst_packet_i.bits.data(2).poke("h112afffd".U)
        i.io.inst_packet_i.bits.data(3).poke("h112afffc".U)
        i.io.inst_packet_i.bits.addr.poke(0.U)
        i.clock.step(2)
        i.io.bpu_inst_packet_o.bits.data(0).expect("h01294820".U)
        i.io.bpu_inst_packet_o.bits.data(1).expect("h112afffe".U)
        i.io.bpu_inst_packet_o.bits.data(2).expect("h112afffd".U)
        i.io.bpu_inst_packet_o.bits.data(3).expect("h112afffc".U)
        i.io.bpu_inst_packet_o.bits.addr.expect(0.U)
        i.io.bpu_inst_packet_o.bits.delay_mask(0).expect(false.B)
        "b0011".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.delay_mask(x._2).expect(x._1)
        })
        "b1110".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.valid_mask(x._2).expect(x._1)
        })
        "b1111".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.predict_mask(x._2).expect(x._1)
        })
        "b0111".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.branch_mask(x._2).expect(x._1)
        })
        i.io.resp_o.bits.predict_addr.expect("h0000_0000".U)
        i.io.resp_o.bits.take_delay.expect(false.B)
        i.clock.step(2)

        i.io.inst_packet_i.bits.data(0).poke("h112affff".U)
        i.io.inst_packet_i.bits.data(1).poke("h112afffe".U)
        i.io.inst_packet_i.bits.data(2).poke("h112afffd".U)
        i.io.inst_packet_i.bits.data(3).poke("h112afffc".U)
        i.io.inst_packet_i.bits.addr.poke(4.U)
        i.clock.step(2)
        i.io.bpu_inst_packet_o.bits.data(0).expect("h112affff".U)
        i.io.bpu_inst_packet_o.bits.data(1).expect("h112afffe".U)
        i.io.bpu_inst_packet_o.bits.data(2).expect("h112afffd".U)
        i.io.bpu_inst_packet_o.bits.data(3).expect("h112afffc".U)
        i.io.bpu_inst_packet_o.bits.addr.expect(4.U)
        "b0111".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.delay_mask(x._2).expect(x._1)
        })
        "b0110".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.valid_mask(x._2).expect(x._1)
        })
        "b1111".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.predict_mask(x._2).expect(x._1)
        })
        "b1111".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
          i.io.bpu_inst_packet_o.bits.branch_mask(x._2).expect(x._1)
        })
        i.io.resp_o.bits.predict_addr.expect("h0000_0000".U)
        i.io.resp_o.bits.take_delay.expect(false.B)
        i.clock.step(2)

    }
  }
}
