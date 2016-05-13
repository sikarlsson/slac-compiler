package slacc
package code

import ast.Trees._
import analyzer.Symbols._
import analyzer.Types._
import analyzer.NameAnalysis._
import cafebabe._
import AbstractByteCodes.{New => _, _}
import ByteCodes._
import utils._

object CodeGeneration extends Pipeline[Program, Unit] {

  def run(ctx: Context)(prog: Program): Unit = {
    import ctx.reporter._

    var labelCount = 0

    def nextLabel(): String = {
      labelCount = labelCount + 1
      currentLabel
    }

    def currentLabel(): String = {
      "label_".concat(labelCount.toString)
    }

    /** Writes the proper .class file in a given directory. An empty string for dir is equivalent to "./". */
    def generateClassFile(sourceName: String, ct: ClassDecl, dir: String): Unit = {
      val classFile = new ClassFile(ct.id.value, None)
      classFile.setSourceFile(sourceName)
      classFile.addDefaultConstructor
      ct.methods foreach {
        meth => {
          if (ct.id.value == "Main") {
            val mainHandler = classFile.addMainMethod.codeHandler
            generateMethodCode(meth)(mainHandler)
          } else {
            val mh: MethodHandler = classFile.addMethod(typeString(meth.retType), meth.id.value, parameterString(meth.args))
            generateMethodCode(meth)(mh.codeHandler)
          }
        }
      }
      classFile.writeToFile(dir + "/" + ct.id.value + ".class")
    }

    // a mapping from variable symbols to positions in the local variables
    // of the stack frame
    def generateMethodCode(mt: MethodDecl)(implicit ch: CodeHandler): Unit = {
      val methSym = mt.getSymbol

      mt.args foreach {
        mArgs => println(mArgs)
      }

      mt.vars foreach {
        mVars => println(mVars)
      }

      mt.exprs :+ mt.retExpr foreach {
        mExpr => generateExprCode(mExpr)(ch)
      }

      ch << (getTypeOfTypeTree(mt.retType, ctx.reporter) match {
        case TInt => { IRETURN }
        case TUnit => { RETURN }
        case _ => { ARETURN }
      })

      ch.print
      ch.freeze
    }

    def generateExprCode(e: ExprTree)(implicit ch: CodeHandler): Unit = {
      e match {
        case And(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << IAND
        }
        case Or(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << IOR
        }
        case Plus(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << IADD
        }
        case Minus(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << ISUB
        }
        case Times(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << IMUL
        }
        case Div(lhs, rhs) => {
          generateExprCode(lhs)
          generateExprCode(rhs)
          ch << IDIV
        }
        case LessThan(lhs, rhs) => {
          val label_1 = nextLabel
          val label_2 = nextLabel
          generateExprCode(rhs)
          generateExprCode(lhs)
          ch << IfLe(label_1) <<Ldc(0) << Goto(label_2) <<
            Label(label_1) << Ldc(1) << Label(label_2)
        }
        case Equals(lhs, rhs) => {
          val label1 = nextLabel
          val label2 = nextLabel
          generateExprCode(lhs)
          generateExprCode(rhs)
        }
        case ArrayRead(arr, index) => {
        }
        case ArrayLength(arr) => {

        }
        case MethodCall(obj, meth, args) => {

        }
        case IntLit(value) => {
          ch << Ldc(value)
        }
        case StringLit(value) => {
          ch << Ldc(value)
        }
        case True() => {
          ch << ICONST_1
        }
        case False() => {
          ch << ICONST_0
        }
        case Identifier(value) => {

        }
        case Self() => {

        }
        case NewIntArray(size) => {

        }
        case New(tpe) => {

        }
        case Not(tpe) => {
          generateExprCode(tpe)
          val label_1 = nextLabel
          val label_2 = nextLabel
          ch << Ldc(1) << If_ICmpNe(label_1) << Ldc(0) << Goto(label_2) <<
            Label(label_1) << Ldc(1) << Label(label_2)
        }
        case Block(exprs) => {
          exprs foreach {
            b => { generateExprCode(b) }
          }
        }
        case If(expr, thn, els) => {
          generateExprCode(expr)
          val label_1 = nextLabel
          val label_2 = nextLabel
          ch << Ldc(1) << If_ICmpEq(label_1)
          els match {
            case Some(e) => {
              generateExprCode(e)
            }
            case _ => {}
          }
          ch << Goto(label_2) << Label(label_1)
          generateExprCode(thn)
          ch << Label(label_2)
        }
        case While(cond, body) => {
          val label1 = nextLabel
          val label2 = nextLabel
          val label3 = nextLabel
          ch << Label(label1)
          generateExprCode(cond)
          ch << Ldc(1) << If_ICmpEq(label2) << Goto(label3) << Label(label2)
          generateExprCode(body)
          ch << Goto(label1) << Label(label3)
        }
        case Println(expr) => {
          ch << GetStatic("java/lang/System", "out", "Ljava/io/PrintStream;") <<
            Ldc("hej") <<
            InvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
        }
        case Assign(id, expr) => {

        }
        case ArrayAssign(id, index, expr) => {

        }
        case Strof(expr) => {

        }
      }
    }

    val outDir = ctx.outDir.map(_.getPath+"/").getOrElse("./")

    val f = new java.io.File(outDir)
    if (!f.exists()) {
      f.mkdir()
    }

    val sourceName = ctx.files.head.getName

    // output code
    prog.classes foreach {
      ct => generateClassFile(sourceName, ct, outDir)
    }

    generateClassFile(sourceName, mainClassDecl, outDir)

  }

  def typeString(retType: TypeTree): String = {
    retType match {
      case IntType() => {
        "I"
      }
      case StringType() => {
        "Ljava/lang/String"
      }
      case UnitType() => {
        "V"
      }
      case BooleanType() => {
        "Z"
      }
      case IntArrayType() => {
        "[I"
      }
      case Identifier(value) => {
        "L".concat(value)
      }
      case _ => {
        sys.error(retType + " has no type!")
      }
    }
  }

  def parameterString(args: List[Formal]): String = {
    var paramStr = "";
    for (arg <- args) {
      paramStr = paramStr.concat(typeString(arg.tpe))
    }
    paramStr
  }
}
