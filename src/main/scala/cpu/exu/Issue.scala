package cpu.exu

import chisel3.{Vec, _}
import chisel3.util._
import chisel3.experimental.{IO, _}
import cpu.ifu.{BranchInfo, FBInstBank}
import instructions.MIPS32._
import signal.Const._
import signal._

import scala.:+

class RenameInfo extends Bundle {
  val is_valid      = Bool()
  val des_rob       = UInt(ROB_IDX_WIDTH.W)
  val op1_rob       = UInt(ROB_IDX_WIDTH.W)
  val op2_rob       = UInt(ROB_IDX_WIDTH.W)
  val op1_regData   = UInt(32.W)
  val op2_regData   = UInt(32.W)
  val op1_in_rob    = Bool()
  val op2_in_rob    = Bool()
  val op1_robData   = UInt(32.W)
  val op2_robData   = UInt(32.W)
  val op1_rob_ready    = Bool()
  val op2_rob_ready    = Bool()
  val inst_addr     = UInt(32.W)
  val uop           = uOP()
  val unit_sel      = UnitSel()
  val need_imm      = Bool()
  val imm_data      = UInt(32.W)
  val predict_taken = Bool()
  def init(): Unit ={
    is_valid      := false.B
    des_rob       := 0.U(ROB_IDX_WIDTH.W)
    op1_rob       := 0.U(ROB_IDX_WIDTH.W)
    op2_rob       := 0.U(ROB_IDX_WIDTH.W)
    op1_regData   := 0.U(32.W)
    op2_regData   := 0.U(32.W)
    op1_robData   := 0.U(32.W)
    op2_robData   := 0.U(32.W)
    op1_in_rob    := false.B
    op2_in_rob    := false.B
    op1_rob_ready := false.B
    op2_rob_ready := false.B
    inst_addr     := 0.U(32.W)
    uop           := uOP.NOP
    unit_sel      := UnitSel.is_Null
    need_imm      := false.B
    imm_data      := 0.U(32.W)
    predict_taken := false.B
  }
}


class IssuerIO(num:Int) extends Bundle {
  val rename_info = Flipped(Valid(Vec(ISSUE_WIDTH, new RenameInfo)))
  val issue_info =Vec(num,Decoupled(Vec(ISSUE_WIDTH, new RSInfo)))
}

class Issuer(rs_num: Int, unit_type: UnitSel.Type) extends Module{
  def leftRotate(i: UInt, n: Int): UInt = {
    val w = i.getWidth
    if (n > 0 && w > 1) {
      Cat(i(w - n - 1, 0), i(w - 1, w - n))
    }
    else {
      i
    }
  }
  val io = IO(new IssuerIO(rs_num))
  val unit_idx = RegInit(1.U(rs_num.W))
  val unit_idxs = Wire(Vec(ISSUE_WIDTH, UInt(rs_num.W)))
  var unit_tmp_idx = unit_idx
  val unit_mask = VecInit(io.rename_info.bits.map(i => i.is_valid && i.unit_sel === unit_type)).map(i => i & io.rename_info.valid)

  for (i <- 0 until ISSUE_WIDTH) {
    unit_idxs(i) := unit_tmp_idx
    unit_tmp_idx = Mux(unit_mask(i), leftRotate(unit_tmp_idx, 1), unit_tmp_idx)
  }

  unit_idx := unit_tmp_idx
  for (i <- 0 until rs_num) {
    io.issue_info(i).valid := io.rename_info.valid
    for (j <- 0 until ISSUE_WIDTH) {
      io.issue_info(i).bits(j).is_valid := unit_idxs(j)(i) && unit_mask(j)
      io.issue_info(i).bits(j).rob_idx          := io.rename_info.bits(j).des_rob
      io.issue_info(i).bits(j).uop              := io.rename_info.bits(j).uop
      io.issue_info(i).bits(j).need_imm         := io.rename_info.bits(j).need_imm
      io.issue_info(i).bits(j).inst_addr        := io.rename_info.bits(j).inst_addr
      io.issue_info(i).bits(j).op1_ready        := !io.rename_info.bits(j).op1_in_rob || io.rename_info.bits(j).op1_rob_ready
      io.issue_info(i).bits(j).op1_tag          := io.rename_info.bits(j).op1_rob
      io.issue_info(i).bits(j).op1_data         := Mux(io.rename_info.bits(j).op1_in_rob,io.rename_info.bits(j).op1_robData,io.rename_info.bits(j).op1_regData)
      io.issue_info(i).bits(j).op2_ready        := !io.rename_info.bits(j).op2_in_rob || io.rename_info.bits(j).op2_rob_ready
      io.issue_info(i).bits(j).op2_tag          := io.rename_info.bits(j).op2_rob
      io.issue_info(i).bits(j).op2_data         := Mux(io.rename_info.bits(j).op2_in_rob,io.rename_info.bits(j).op2_robData,io.rename_info.bits(j).op2_regData)
      io.issue_info(i).bits(j).imm_data         := io.rename_info.bits(j).imm_data
      io.issue_info(i).bits(j).predict_taken    := io.rename_info.bits(j).predict_taken
    }
  }
}

class IssueIO extends Bundle{
  val rename_info = Flipped(Valid(Vec(ISSUE_WIDTH, new RenameInfo)))
  val rob_read = Vec(ISSUE_WIDTH, new RobReadIO())
  val wb_info    = Vec(5, Flipped(Valid(new WriteBackInfo)))
  val dispatch_info = Vec(5,Decoupled(new DispatchInfo))
  val need_flush = Input(Bool())
  val need_stop= Output(Bool())
}

class Issue extends Module{
  val io               = IO(new IssueIO)
  val alu_issuer       = Module(new Issuer(2, UnitSel.is_Alu))
  val bju_issuer       = Module(new Issuer(1, UnitSel.is_Bju))
  val mdu_issuer       = Module(new Issuer(1, UnitSel.is_Mdu))
  val lsu_issuer       = Module(new Issuer(1, UnitSel.is_Mem))
  val issuers          = alu_issuer :: bju_issuer :: mdu_issuer :: lsu_issuer :: Nil
  val alu_rs           = Seq.fill(2)(Module(new ReservationStation(4)))
  val bju_rs           = Seq.fill(1)(Module(new ReservationStation(4)))
  val mdu_rs           = Seq.fill(1)(Module(new ReservationStation(4)))
  val lsu_rs           = Seq.fill(1)(Module(new ReservationStation(8)))
  val reserve_stations = alu_rs :: bju_rs :: mdu_rs :: lsu_rs :: Nil

  val rename_info = Wire(Vec(ISSUE_WIDTH, new RenameInfo))
  val rename_info_valid = WireInit(false.B)
  rename_info       :=  io.rename_info.bits
  rename_info_valid :=  io.rename_info.valid

  val op1_in_wb = Wire(Vec(ISSUE_WIDTH,Bool()))
  val op2_in_wb = Wire(Vec(ISSUE_WIDTH,Bool()))
  val op1_wbData = Wire(Vec(ISSUE_WIDTH,UInt(32.W)))
  val op2_wbData = Wire(Vec(ISSUE_WIDTH,UInt(32.W)))
  for (i<-0 until ISSUE_WIDTH){
    op1_in_wb(i):= io.wb_info.map(wb=> wb.bits.rob_idx === rename_info(i).op1_rob && wb.valid ).reduce(_|_)
    op2_in_wb(i):= io.wb_info.map(wb=> wb.bits.rob_idx === rename_info(i).op2_rob && wb.valid ).reduce(_|_)
    op1_wbData(i) := io.wb_info.map(wb=> Fill(32,wb.bits.rob_idx === rename_info(i).op1_rob && wb.valid) & wb.bits.data ).reduce(_|_)
    op2_wbData(i) := io.wb_info.map(wb=> Fill(32,wb.bits.rob_idx === rename_info(i).op2_rob && wb.valid) & wb.bits.data ).reduce(_|_)
  }

  for (i <- 0 until ISSUE_WIDTH){
    io.rob_read(i).op1_idx := rename_info(i).op1_rob
    io.rob_read(i).op2_idx := rename_info(i).op2_rob
    rename_info(i).op1_robData := Mux(op1_in_wb(i),op1_wbData(i),io.rob_read(i).op1_data)
    rename_info(i).op2_robData := Mux(op2_in_wb(i),op2_wbData(i),io.rob_read(i).op2_data)
    rename_info(i).op1_rob_ready := io.rob_read(i).op1_ready || op1_in_wb(i)
    rename_info(i).op2_rob_ready := io.rob_read(i).op2_ready || op2_in_wb(i)
  }

  val all_ready = reserve_stations.map(_.map(_.io.issue_info.ready).reduce(_&_)).reduce(_&_)

  issuers.zip(reserve_stations).foreach{case (issuer,reserve_station)=>
    issuer.io.issue_info.zip(reserve_station.map(_.io.issue_info)).foreach{case (i,rs)=>
      rs<>i
    }
  }

  issuers.foreach(i=>{
    i.io.rename_info.bits:=rename_info
    i.io.rename_info.valid:=rename_info_valid
  })
  reserve_stations.foreach(reserve_station=>{
    reserve_station.foreach(rs=>{
      rs.io.wb_info:=io.wb_info
      rs.io.need_flush:=io.need_flush
      rs.io.need_stop:= !all_ready
    })
  })

  io.dispatch_info(0)<>alu_rs(0).io.dispatch_info
  io.dispatch_info(1)<>alu_rs(1).io.dispatch_info
  io.dispatch_info(2)<>bju_rs(0).io.dispatch_info
  io.dispatch_info(3)<>mdu_rs(0).io.dispatch_info
  io.dispatch_info(4)<>lsu_rs(0).io.dispatch_info

  io.need_stop := !all_ready


}
