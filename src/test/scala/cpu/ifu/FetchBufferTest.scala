//package cpu.ifu
//
//import chisel3._
//import chiseltest._
//import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
//import chiseltest.internal.WriteVcdAnnotation
//import org.scalatest.FlatSpec
//
//class FetchBufferTest extends FlatSpec with ChiselScalatestTester {
//
//  "FetchBuffer" should "deq and enq" in {
//    test(new FetchBuffer).withAnnotations(Seq(WriteVcdAnnotation)){
//      i=>
//        //enq 1
//        i.io.clear_i.poke(false.B)
//        i.io.bpu_inst_packet_i.valid.poke(true.B)
//        i.io.bpu_inst_packet_i.bits.data(0).poke("h11111111".U)
//        i.io.bpu_inst_packet_i.bits.data(1).poke("h22222222".U)
//        i.io.bpu_inst_packet_i.bits.data(2).poke("h33333333".U)
//        i.io.bpu_inst_packet_i.bits.data(3).poke("h44444444".U)
//        "b1000".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
//          i.io.bpu_inst_packet_i.bits.valid_mask(x._2).poke(x._1)
//        })
//        i.io.inst_bank.valid.expect(false.B)
//        i.io.bpu_inst_packet_i.ready.expect(true.B)
//        i.clock.step(1)
//        //enq 2
//        i.io.bpu_inst_packet_i.valid.poke(true.B)
//        "b1100".U(4.W).asBools().reverse.zipWithIndex.foreach(x=>{
//          i.io.bpu_inst_packet_i.bits.valid_mask(x._2).poke(x._1)
//        })
//        i.io.inst_bank.valid.expect(true.B)
//        i.io.bpu_inst_packet_i.ready.expect(true.B)
//        i.clock.step(1)
//        //deq 2
//        i.io.bpu_inst_packet_i.valid.poke(false.B)
//        i.io.inst_bank.valid.expect(true.B)
//        i.io.bpu_inst_packet_i.ready.expect(true.B)
//        i.clock.step(1)
//        //deq 1
//        i.io.bpu_inst_packet_i.valid.poke(false.B)
//        i.io.inst_bank.valid.expect(true.B)
//        i.io.bpu_inst_packet_i.ready.expect(true.B)
//        i.clock.step(1)
//        //no deq
//        i.io.bpu_inst_packet_i.valid.poke(false.B)
//        i.io.inst_bank.valid.expect(false.B)
//        i.io.bpu_inst_packet_i.ready.expect(true.B)
//        i.clock.step(1)
//
//    }
//  }
//}