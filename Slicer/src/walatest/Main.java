package walatest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.text.html.HTMLDocument.Iterator;
import javax.xml.soap.Node;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.thin.ThinSlicer;
import com.ibm.wala.shrikeBT.analysis.ClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

public class Main {
	public static void main(String args[]) throws IOException, ClassHierarchyException, IllegalArgumentException,
	InvalidClassFileException, CancelException {
File exFile = new FileProvider().getFile("../Java60RegressionExclusions.txt");

AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope("../list/bin/list/", exFile);// 分析的路径，文件夹或jar包

com.ibm.wala.ipa.cha.ClassHierarchy cha = ClassHierarchyFactory.make(scope); // 循环遍历每一个类

Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
Iterable<Entrypoint> entrypoints1 = makeEntrypoints(entrypoints);

AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
options.setReflectionOptions(ReflectionOptions.FULL);
AnalysisOptions options1 = new AnalysisOptions(scope, entrypoints1);
options1.setReflectionOptions(ReflectionOptions.FULL);

CallGraphBuilder<InstanceKey> builder = Util.makeZeroCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(),
		cha, scope);// okokokokok


CallGraph cg = builder.makeCallGraph(options1, null);
//print(cg);
ModRef<InstanceKey> modRef = ModRef.make();
final PointerAnalysis<InstanceKey> pa = builder.getPointerAnalysis();

SDG<?> sdg = new SDG<>(cg, pa, modRef, DataDependenceOptions.FULL,
		ControlDependenceOptions.NO_EXCEPTIONAL_EDGES, null);

//printSDG(sdg,"linkFirst");

sliceCallStatement("element", 2, sdg, cg, pa);


}
 

	public static Iterable<Entrypoint> makeEntrypoints(Iterable<Entrypoint> entrypoints) {
		final HashSet<Entrypoint> result = HashSetFactory.make();

		for (Entrypoint E : entrypoints) {
			if (E.getMethod().getName().toString().equals("element")) {
				System.out.println("Entrypoint: " + E);
				result.add(E);
			}
		}
		return result::iterator;
	}

	
	public static void print(CallGraph cg) {
		int i = 1;
		for (java.util.Iterator<CGNode> it = cg.iterator(); it.hasNext();) {

			CGNode n = it.next(); // 函数名和类名比较
//			System.out.println("method:  " + n.getMethod().getName() + "    class:  "
//					+ n.getMethod().getDeclaringClass().getName().toString());
			System.err.println(n);

		}

	}
	
	public static int printState(Statement s) {
	// 直接输出该statement
	// System.out.println("cgnode:" + s);
	if (s.getKind() == Statement.Kind.NORMAL
			&& !(s.getNode().getMethod().getName().equals(Atom.findOrCreateAsciiAtom("fakeRootMethod")))) {
		CGNode node = s.getNode();

		// 对应的method：
		IMethod method = node.getMethod();

		// 对应的class：
		IClass klass = s.getNode().getMethod().getDeclaringClass();

		// 输出类和方法
		System.out.println("method:" + method.getName() + ", class:" + klass.getName());
		// 输出行
		if (s.getKind() == Statement.Kind.NORMAL) { // ignore special kinds of statements
			int bcIndex, instructionIndex = ((NormalStatement) s).getInstructionIndex();
			try {
				bcIndex = ((ShrikeBTMethod) s.getNode().getMethod()).getBytecodeIndex(instructionIndex);
				try {
					int src_line_number = s.getNode().getMethod().getLineNumber(bcIndex);
					System.out.println("Source line number = " + src_line_number);
					return src_line_number;
				} catch (Exception e) {
					System.err.println("Bytecode index no good");
					System.err.println(e.getMessage());
				}
			} catch (Exception e) {
				System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
				System.err.println(e.getMessage());
			}
		}
	}
	return -1;

}

	public static void printSDG(SDG<?> sdg, String name) {
		int i = 0;
		for (java.util.Iterator<Statement> it = sdg.iterator(); it.hasNext();) {
			i = 0;
			Statement state = it.next(); // 函数名和类名比较
			if (state.getNode().getMethod().getName().toString().contains(name)) {
				if (state.getKind() == Statement.Kind.NORMAL) { // ignore special kinds of statements
					int bcIndex, instructionIndex = ((NormalStatement) state).getInstructionIndex();
					try {
						bcIndex = ((ShrikeBTMethod) state.getNode().getMethod()).getBytecodeIndex(instructionIndex);
						try {
							i = state.getNode().getMethod().getLineNumber(bcIndex);

						} catch (Exception e) {
							System.err.println("Bytecode index no good");
							System.err.println(e.getMessage());
						}
					} catch (Exception e) {
						System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
						i = 1;
						System.err.println(e.getMessage());
					}
				}
				System.out.println("statement:  " + state.toString());
				if (state.getKind() == Statement.Kind.NORMAL)
					System.out.println("----------" + i + ((NormalStatement) state).getInstruction().toString());
				if (state.getKind() == Statement.Kind.PARAM_CALLEE)
					System.out.println("----------" + i + ((ParamCallee) state).getValueNumber());
				System.out.println();
				// System.err.println(n);

			}
		}
	}

	
	public static void printSDG(SDG<?> sdg) {
		int i = 0;
		for (java.util.Iterator<Statement> it = sdg.iterator(); it.hasNext();) {
			i = 0;
			Statement state = it.next(); // 函数名和类名比较

			if (state.getKind() == Statement.Kind.NORMAL) { // ignore special kinds of statements
				int bcIndex, instructionIndex = ((NormalStatement) state).getInstructionIndex();
				try {
					bcIndex = ((ShrikeBTMethod) state.getNode().getMethod()).getBytecodeIndex(instructionIndex);
					try {
						i = state.getNode().getMethod().getLineNumber(bcIndex);

					} catch (Exception e) {
						System.err.println("Bytecode index no good");
						System.err.println(e.getMessage());
					}
				} catch (Exception e) {
					System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
					i = 1;
					System.err.println(e.getMessage());
				}
			}
			System.out.println("statement:  " + state.toString());
			if (state.getKind() == Statement.Kind.NORMAL)
				System.out.println("----------" + i + ((NormalStatement) state).getInstruction().toString());
//			if (state.getKind() == Statement.Kind.PARAM_CALLEE)
//				System.out.println("----------" + i + ((ParamCallee) state).getValueNumber());
			System.out.println();
			// System.err.println(n);

		}
	}
	public static Statement getStatement(Statement state, String method, int value, String kind) {
		if (state.getKind() == Statement.Kind.NORMAL && kind.equals("return")
				&& state.getNode().getMethod().getName().toString().equals(method)) {
			if (((NormalStatement) state).getInstruction().toString().contains("return 4")) {
				return state;
			}
			;
		}
		if (state.getKind() == Statement.Kind.NORMAL && kind.equals("line")
				&& state.getNode().getMethod().getName().toString().equals(method)) {
			if (((NormalStatement) state).getInstruction().toString().contains("putfield 1.< Application, Llist/LinkedList, modCount")) {
				return state;
			}
			;
		}
		if (state.getKind() == Statement.Kind.PARAM_CALLEE && kind.equals("call")) {
			if (((ParamCallee) state).getValueNumber() == value
					&& state.getNode().getMethod().getName().toString().equals(method)) {
				return state;
			}
			;
		}
		if (state.getKind() == Statement.Kind.PARAM_CALLER && kind.equals("caller")) {
			if (state.getNode().getMethod().getName().toString().equals(method)) {
				return state;
			}
			;
		}
//		if (state.getKind() == Statement.Kind.NORMAL_RET_CALLEE&& kind.equals("retcall")) {
//			// System.out.println(((NormalStatement) state).getInstruction().toString()+"
//			// "+state.getNode().getMethod().getName().toString());
//			if (state.getNode().getMethod().getName().toString().contains("getMin")) {
//				return state;
//			}
//			;
//		}
		return null;
	}

	public static void sliceCallStatement(String method, int callValue, SDG<?> sdg, CallGraph cg,
		PointerAnalysis<InstanceKey> pa) throws IllegalArgumentException, CancelException, IOException {
//	printSDG(sdg, method);
	Statement state = null;
	Collection<Statement> slice = null,sameLineState=null;
	ArrayList<Statement> as=null;
	int k=0;
	for (java.util.Iterator<Statement> it = sdg.iterator(); it.hasNext();) {
		k++;
		state = it.next();
		String kind = "return";// 函数名和类名比较
		state = getStatement(state, method, callValue, kind);
		if (state != null) {
			System.err.println("Statement: " + state);
			System.out.println(k);
//			if (kind.equals("call") || kind.equals("caller") || kind.equals("line")) {
//				slice = Slicer.computeBackwardSlice(sdg, state);
//			} else {
//
//				slice = Slicer.computeBackwardSlice(sdg, state);
//			}
			slice = Slicer.computeBackwardSlice(sdg, state);
			break;
		}
	}

	// 遍历切片产生的statement
	System.out.println();
	ArrayList<Integer> lines = new ArrayList<Integer>();
	System.out.println(slice);
	for (Statement s : slice) {
		if (s.getKind() == Statement.Kind.NORMAL) {
			int temp=printState(s);
			if(temp!=-1) {
			lines.add(temp);;
			}
		}
	}
	System.out.println(lines);
	
}
}
