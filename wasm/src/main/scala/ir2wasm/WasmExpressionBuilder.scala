package wasm
package ir2wasm

import org.scalajs.ir.{Trees => IRTrees}
import org.scalajs.ir.{Types => IRTypes}
import org.scalajs.ir.{Names => IRNames}

import wasm4s._
import wasm4s.WasmInstr._
import wasm4s.WasmImmediate._

class WasmExpressionBuilder(ctx: WasmContext, fctx: WasmFunctionContext) {
  /** object creation prefix
    * ```
    * local.get $receiver ;; (ref null $struct)
    * ref.is_null
    * if
    *   struct.new_default $struct
    *   local.set $receiver
    * end
    * ```
    */
  def objectCreationPrefix(method: IRTrees.MethodDef): List[WasmInstr] = {
    assert(method.flags.namespace.isConstructor)
    val recieverGCTypeName = fctx.receiver.typ match {
      case Types.WasmRefNullType(Types.WasmHeapType.Type(gcType)) => gcType
      case Types.WasmRefType(Types.WasmHeapType.Type(gcType))     => gcType
      case _                                                      => ???
    }
    List(
      LOCAL_GET(LocalIdx(fctx.receiver.name)),
      REF_IS_NULL,
      IF(WasmImmediate.BlockType.ValueType(None)),
      STRUCT_NEW_DEFAULT(TypeIdx(recieverGCTypeName)),
      LOCAL_SET(LocalIdx(fctx.receiver.name)),
      END
    )
  }

  def transformTree(tree: IRTrees.Tree): List[WasmInstr] = {
    tree match {
      case t: IRTrees.Literal    => List(transformLiteral(t))
      case t: IRTrees.UnaryOp    => transformUnaryOp(t)
      case t: IRTrees.BinaryOp   => transformBinaryOp(t)
      case t: IRTrees.VarRef     => List(transformVarRef(t))
      case t: IRTrees.LoadModule => transformLoadModule(t)
      case t: IRTrees.StoreModule =>
        println(t.show)
        transformStoreModule(t)
      case t: IRTrees.This => // push receiver to the stack
        List(LOCAL_GET(LocalIdx(fctx.receiver.name)))
      case t: IRTrees.ApplyStatic => ???
      case t: IRTrees.ApplyStatically =>
        println(t.receiver)
        if (t.className.nameString == "java.lang.Object") Nil
        else transformApplyStatically(t)
      case t: IRTrees.Apply              => transformApply(t)
      case t: IRTrees.ApplyDynamicImport => ???
      case t: IRTrees.Block              => ???
      case t: IRTrees.Select             => transformSelect(t)
      case t: IRTrees.Assign             => transformAssign(t)
      case t: IRTrees.VarDef             => transformVarDef(t)
      case _ =>
        println(tree)
        ???

      // case undef: IRTrees.Undefined => ???
      // case unary: IRTrees.JSUnaryOp => ???
      // case select: IRTrees.JSPrivateSelect => ???
      // case nul: IRTrees.Null => ???
      // case v: IRTrees.UnwrapFromThrowable => ???
      // case v: IRTrees.New => ???
      // case IRTrees.Assign(pos) =>
      // case IRTrees.RecordValue(pos) =>
      // case IRTrees.JSTypeOfGlobalRef(pos) =>
      // case IRTrees.JSMethodApply(pos) =>
      // case IRTrees.Debugger(pos) =>
      // case IRTrees.JSNewTarget(pos) =>
      // case IRTrees.SelectStatic(tpe) =>
      // case IRTrees.IsInstanceOf(pos) =>
      // case IRTrees.JSLinkingInfo(pos) =>
      // case IRTrees.Select(tpe) =>
      // case IRTrees.Return(pos) =>
      // case IRTrees.ArrayLength(pos) =>
      // case IRTrees.While(pos) =>
      // case IRTrees.LoadJSConstructor(pos) =>
      // case IRTrees.JSSuperMethodCall(pos) =>
      // case IRTrees.NewArray(pos) =>
      // case IRTrees.Match(tpe) =>
      // case IRTrees.Throw(pos) =>
      // case IRTrees.JSNew(pos) =>
      // case IRTrees.Closure(pos) =>
      // case IRTrees.JSGlobalRef(pos) =>
      // case IRTrees.JSBinaryOp(pos) =>
      // case IRTrees.JSObjectConstr(pos) =>
      // case IRTrees.RecordSelect(tpe) =>
      // case IRTrees.AsInstanceOf(pos) =>
      // case IRTrees.If(tpe) =>
      // case IRTrees.TryFinally(pos) =>
      // case IRTrees.Labeled(pos) =>
      // case IRTrees.SelectJSNativeMember(pos) =>
      // case IRTrees.ClassOf(pos) =>
      // case IRTrees.GetClass(pos) =>
      // case IRTrees.JSImportMeta(pos) =>
      // case IRTrees.JSSuperSelect(pos) =>
      // case IRTrees.ArraySelect(tpe) =>
      // case IRTrees.JSSelect(pos) =>
      // case IRTrees.Skip(pos) =>
      // case IRTrees.LoadJSModule(pos) =>
      // case IRTrees.JSFunctionApply(pos) =>
      // case IRTrees.WrapAsThrowable(pos) =>
      // case IRTrees.JSSuperConstructorCall(pos) =>
      // case IRTrees.Clone(pos) =>
      // case IRTrees.CreateJSClass(pos) =>
      // case IRTrees.Transient(pos) =>
      // case IRTrees.ArrayValue(pos) =>
      // case IRTrees.JSDelete(pos) =>
      // case IRTrees.ForIn(pos) =>
      // case IRTrees.JSArrayConstr(pos) =>
      // case vd: IRTrees.VarDef => ???
      // case tc: IRTrees.TryCatch => ???
      // case IRTrees.JSImportCall(pos) =>
      // case IRTrees.IdentityHashCode(pos) =>
    }

  }

  private def transformAssign(t: IRTrees.Assign): List[WasmInstr] = {
    val wasmRHS = transformTree(t.rhs)
    val setInstruction: List[WasmInstr] =
      t.lhs match {
        case sel: IRTrees.Select =>
          val className = Names.WasmGCTypeName.fromIR(sel.className)
          val fieldName = Names.WasmFieldName.fromIR(sel.field.name)
          transformTree(sel.qualifier) :+
            STRUCT_SET(TypeIdx(className), StructFieldIdx(fieldName))
        case sel: IRTrees.SelectStatic => // OK?
          val className = Names.WasmGCTypeName.fromIR(sel.className)
          val fieldName = Names.WasmFieldName.fromIR(sel.field.name)
          List(
            GLOBAL_GET(GlobalIdx(Names.WasmGlobalName.forModuleClassInstance(sel.className))),
            STRUCT_SET(TypeIdx(className), StructFieldIdx(fieldName))
          )
        case assign: IRTrees.ArraySelect     => ??? // array.set
        case assign: IRTrees.RecordSelect    => ??? // struct.set
        case assign: IRTrees.JSPrivateSelect => ???
        case assign: IRTrees.JSSelect        => ???
        case assign: IRTrees.JSSuperSelect   => ???
        case assign: IRTrees.JSGlobalRef     => ???
        case ref: IRTrees.VarRef =>
          List(LOCAL_SET(LocalIdx(Names.WasmLocalName.fromIR(ref.ident.name))))
      }
      wasmRHS ++ setInstruction
  }

  private def transformApply(t: IRTrees.Apply): List[WasmInstr] = {
    val wasmArgs = transformTree(t.receiver) ++ t.args.flatMap(transformTree)
    val funcName = Names.WasmFunctionName.fromIR(t.method.name)
    wasmArgs :+ CALL(FuncIdx(funcName))
  }
  private def transformApplyStatically(t: IRTrees.ApplyStatically): List[WasmInstr] = {
    val wasmArgs = transformTree(t.receiver) ++ t.args.flatMap(transformTree)
    val funcName = Names.WasmFunctionName.fromIR(t.method.name)
    wasmArgs :+ CALL(FuncIdx(funcName))
  }

  private def transformLiteral(l: IRTrees.Literal): WasmInstr = l match {
    case IRTrees.BooleanLiteral(v) => WasmInstr.I32_CONST(if (v) I32(1) else I32(0))
    case IRTrees.ByteLiteral(v)    => WasmInstr.I32_CONST(I32(v))
    case IRTrees.ShortLiteral(v)   => WasmInstr.I32_CONST(I32(v))
    case IRTrees.IntLiteral(v)     => WasmInstr.I32_CONST(I32(v))
    case IRTrees.CharLiteral(v)    => WasmInstr.I32_CONST(I32(v))
    case IRTrees.LongLiteral(v)    => WasmInstr.I64_CONST(I64(v))
    case IRTrees.FloatLiteral(v)   => WasmInstr.F32_CONST(F32(v))
    case IRTrees.DoubleLiteral(v)  => WasmInstr.F64_CONST(F64(v))

    case v: IRTrees.Undefined     => ???
    case v: IRTrees.Null          => ???
    case v: IRTrees.StringLiteral => ???
    case v: IRTrees.ClassOf       => ???
  }

  private def transformSelect(sel: IRTrees.Select): List[WasmInstr] = {
    val className = Names.WasmGCTypeName.fromIR(sel.className)
    val fieldName = Names.WasmFieldName.fromIR(sel.field.name)
    transformTree(sel.qualifier) :+
      STRUCT_GET(TypeIdx(className), StructFieldIdx(fieldName))
  }

  private def transformStoreModule(t: IRTrees.StoreModule): List[WasmInstr] = {
    val name = Names.WasmGlobalName.forModuleClassInstance(t.className)
    transformTree(t.value) :+ GLOBAL_SET(GlobalIdx(name))
  }

  // push the module to the stack
  private def transformLoadModule(t: IRTrees.LoadModule): List[WasmInstr] = {
    val tyName = Names.WasmGCTypeName.fromIR(t.tpe.className)
    val gName = Names.WasmGlobalName.forModuleClassInstance(t.className)
    val ctroName =
      // TODO (design): maybe we should resolve functions with some other ways?
      // or maybe we should index all the names of class and methods before traversing/transforming the given tree.
      IRNames.MethodName(
        IRNames.SimpleMethodName.Constructor,
        Nil,
        IRTypes.VoidRef
      )
    val wasmCtorName = Names.WasmFunctionName.fromIR(ctroName)
    List(
      // global.get $module_name
      // ref.if_null
      //   ref.null $module_type
      //   call $module_init ;; should set to global
      // end
      // global.get $module_name
      GLOBAL_GET(GlobalIdx(gName)), // [rt]
      REF_IS_NULL, // [rt] -> [i32] (bool)
      IF(WasmImmediate.BlockType.ValueType(None)),
      REF_NULL(HeapType(Types.WasmHeapType.Type(tyName))),
      CALL(FuncIdx(wasmCtorName)),
      END,
      GLOBAL_GET(GlobalIdx(gName)) // [rt]
    )
  }

  private def transformUnaryOp(unary: IRTrees.UnaryOp): List[WasmInstr] = {
    ???
  }

  private def transformBinaryOp(binary: IRTrees.BinaryOp): List[WasmInstr] = {
    import IRTrees.BinaryOp
    val lhsInstrs = transformTree(binary.lhs)
    val rhsInstrs = transformTree(binary.rhs)
    val operation = binary.op match {
      case BinaryOp.===      => ???
      case BinaryOp.!==      => ???
      case BinaryOp.String_+ => ???

      case BinaryOp.Boolean_== => I32_EQ
      case BinaryOp.Boolean_!= => I32_NE
      case BinaryOp.Boolean_|  => I32_OR
      case BinaryOp.Boolean_&  => I32_AND

      case BinaryOp.Int_+   => I32_ADD
      case BinaryOp.Int_-   => I32_SUB
      case BinaryOp.Int_*   => I32_MUL
      case BinaryOp.Int_/   => I32_DIV_S // signed division
      case BinaryOp.Int_%   => I32_REM_S // signed remainder
      case BinaryOp.Int_|   => ???
      case BinaryOp.Int_&   => ???
      case BinaryOp.Int_^   => ???
      case BinaryOp.Int_<<  => ???
      case BinaryOp.Int_>>> => ???
      case BinaryOp.Int_>>  => ???
      case BinaryOp.Int_==  => I32_EQ
      case BinaryOp.Int_!=  => I32_NE
      case BinaryOp.Int_<   => I32_LT_S
      case BinaryOp.Int_<=  => I32_LE_S
      case BinaryOp.Int_>   => I32_GT_S
      case BinaryOp.Int_>=  => I32_GE_S

      case BinaryOp.Long_+   => I64_ADD
      case BinaryOp.Long_-   => I64_SUB
      case BinaryOp.Long_*   => I64_MUL
      case BinaryOp.Long_/   => I64_DIV_S
      case BinaryOp.Long_%   => I64_REM_S
      case BinaryOp.Long_|   => ???
      case BinaryOp.Long_&   => ???
      case BinaryOp.Long_^   => ???
      case BinaryOp.Long_<<  => ???
      case BinaryOp.Long_>>> => ???
      case BinaryOp.Long_>>  => ???

      case BinaryOp.Long_== => I64_EQ
      case BinaryOp.Long_!= => I64_NE
      case BinaryOp.Long_<  => I64_LT_S
      case BinaryOp.Long_<= => I64_LE_S
      case BinaryOp.Long_>  => I64_GT_S
      case BinaryOp.Long_>= => I64_GE_S

      case BinaryOp.Float_+ => F32_ADD
      case BinaryOp.Float_- => F32_SUB
      case BinaryOp.Float_* => F32_MUL
      case BinaryOp.Float_/ => F32_DIV

      // TODO
      // get_local 0
      // get_local 1
      // f32.div
      // f32.trunc
      // get_local 1
      // f32.mul
      // f32.sub
      case BinaryOp.Float_% => ???

      case BinaryOp.Double_+ => F64_ADD
      case BinaryOp.Double_- => F64_SUB
      case BinaryOp.Double_* => F64_MUL
      case BinaryOp.Double_/ => F64_DIV
      case BinaryOp.Double_% => ??? // TODO same as Float_%

      case BinaryOp.Double_== => F64_EQ
      case BinaryOp.Double_!= => F64_NE
      case BinaryOp.Double_<  => F64_LT
      case BinaryOp.Double_<= => F64_LE
      case BinaryOp.Double_>  => F64_GT
      case BinaryOp.Double_>= => F64_GE

      // // New in 1.11
      case BinaryOp.String_charAt => ??? // TODO
    }
    lhsInstrs ++ rhsInstrs :+ operation
  }

  private def transformVarRef(r: IRTrees.VarRef): LOCAL_GET = {
    val name = Names.WasmLocalName.fromIR(r.ident.name)
    LOCAL_GET(LocalIdx(name))
  }

  private def transformVarDef(r: IRTrees.VarDef): List[WasmInstr] = {
    val local = WasmLocal(
      Names.WasmLocalName.fromIR(r.name.name),
      TypeTransformer.transform(r.vtpe)(ctx),
      isParameter = false
    )
    fctx.locals.define(local)

    transformTree(r.rhs) :+ LOCAL_SET(LocalIdx(local.name))
  }

}