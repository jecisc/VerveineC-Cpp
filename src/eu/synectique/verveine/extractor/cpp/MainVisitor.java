package eu.synectique.verveine.extractor.cpp;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.Map;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTArrayModifier;
import org.eclipse.cdt.core.dom.ast.IASTAttribute;
import org.eclipse.cdt.core.dom.ast.IASTAttributeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTComment;
import org.eclipse.cdt.core.dom.ast.IASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier.IASTEnumerator;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTPointerOperator;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTToken;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTTypeId;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.c.ICASTDesignator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCapture;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTClassVirtSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCompositeTypeSpecifier.ICPPASTBaseSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDecltypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTElaboratedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamespaceDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateParameter;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTVirtSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPConstructor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.internal.core.dom.parser.ASTAmbiguousNode;

import eu.synectique.verveine.core.Dictionary;
import eu.synectique.verveine.core.EntityStack;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.Entity;
import eu.synectique.verveine.core.gen.famix.Function;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.Namespace;

public class MainVisitor extends VerveineVisitor {

	/**
	 * A stack that keeps the current definition context (package/class/method)
	 */
	protected EntityStack context;

	/**
	 * The source code of the visited AST.
	 * Used to find back the contents of non-javadoc comments
	 */
	protected RandomAccessFile source;

	/**
	 * Whether a variable access is lhs (write) or not
	 */
	protected boolean inAssignmentLHS = false;

	
	public MainVisitor(CDictionary dico) {
		super(dico);
		this.context = new EntityStack();
	}

	@Override
	public int visit(IASTTranslationUnit node) {
//		traceup("IASTTranslationUnit: "+node.getFilePath());
/*
		// As this is the first node visited in an AST, set's the source file for this AST
		try {
			source = new RandomAccessFile( node.getFilePath(), "r");
		} catch (FileNotFoundException e) {
			source = null;
			e.printStackTrace();
		}
*/
		return super.visit(node);
	}

	@Override
	public int visit(ICPPASTNamespaceDefinition node) {
//		traceup("ICPPASTNamespaceDefinition: "+node.getName());
		IASTName nodeName = node.getName();
		Namespace fmx = dico.ensureFamixNamespace(nodeName.resolveBinding(), nodeName.toString());
			fmx.setIsStub(false);
		
		this.context.pushPckg(fmx);
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTNamespaceDefinition node) {
		this.context.popPckg();
//		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTArrayModifier node) {
//		traceup("TRACE, Visiting IASTArrayModifier ");
		return super.visit(node);
	}

	@Override
	public int visit(IASTDeclarator node) {
//		traceup("IASTDeclarator:");

		if (node instanceof IASTFunctionDeclarator) {
			this.visit((IASTFunctionDeclarator)node);
		}

		return super.visit(node);
	}

	@Override
	public int leave(IASTDeclarator node) {
		if (node instanceof IASTFunctionDeclarator) {
			this.leave((IASTFunctionDeclarator)node);
		}

//		tracedown("IASTDeclarator: ");
//		tracename(node.getName());
		return super.leave(node);
	}

	@Override
	public int visit(IASTDeclSpecifier node) {
//		String trace=node.getRawSignature();
//		int cr = trace.indexOf('\n');
//		traceup("IASTDeclSpecifier: "+ (cr<0 ? trace : trace.substring(0, cr)+"..."));

		if (node instanceof ICPPASTElaboratedTypeSpecifier) {
			// -> struct/class)
		}
		else if (node instanceof ICPPASTNamedTypeSpecifier) {
			// -> struct/class)
		}
		else if (node instanceof IASTCompositeTypeSpecifier) {
			this.visit( (IASTCompositeTypeSpecifier)node );
		}
		return super.visit(node);
	}

	@Override
	public int leave(IASTDeclSpecifier node) {
//		tracedown();

		if (node instanceof ICPPASTElaboratedTypeSpecifier) {
			// -> struct/class)
		}
		else if (node instanceof ICPPASTNamedTypeSpecifier) {
			// -> struct/class)
		}
		else if (node instanceof IASTCompositeTypeSpecifier) {
			this.leave( (IASTCompositeTypeSpecifier)node );
		}

		return super.leave(node);
	}

	@Override
	public int visit(IASTEnumerator node) {
//		traceup("IASTEnumerator ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTEnumerator node) {
//		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTExpression node) {
		tracemsg("IASTExpression ("+node.getClass().getSimpleName()+")");
		if (node instanceof IASTFunctionCallExpression) {
			traceup("FunctionCall name:");
			visit( ((IASTFunctionCallExpression)node).getFunctionNameExpression() );
			tracedown();
			return ASTVisitor.PROCESS_SKIP;  // because we already visited the FunctionNameExpression
		}
		else if (node instanceof IASTIdExpression) {
			IASTName idName = ((IASTIdExpression) node).getName();
			tracemsg("    ->  IdExpression:");
			tracename(idName);
			traceanchor(idName.getImageLocation());
		}
		else if (node instanceof IASTFieldReference) {
			tracemsg("    ->  FieldReference:");
			tracename( ((IASTFieldReference)node).getFieldName() );
			traceup("Field owner:");
			visit( ((IASTFieldReference)node).getFieldOwner() );
			tracedown();
		}
		return super.visit(node);
	}

	@Override
	public int leave(IASTExpression node) {
		// never called
		return super.leave(node);
	}

	@Override
	public int visit(IASTInitializer node) {
//		traceup("IASTInitializer ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTInitializer node) {
//		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTParameterDeclaration node) {
//		tracemsg("IASTParameterDeclaration: ");
		if (context.topMethod() != null) {
			node.accept( new ParamDeclVisitor(dico, context.topMethod()) );
		}
		return PROCESS_SKIP;
	}

	@Override
 	public int leave(IASTParameterDeclaration node) {
		// never actually called
		return super.leave(node);
	}

	@Override
	public int visit(IASTPointerOperator node) {
		traceup("IASTPointerOperator ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTPointerOperator node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTProblem node) {
		traceup("IASTProblem ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTProblem node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTStatement node) {
		traceup("IASTStatement ("+node.getClass().getSimpleName()+")");
		return super.visit(node);
	}

	@Override
	public int leave(IASTStatement node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTTypeId node) {
		traceup("IASTTypeId ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTTypeId node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICASTDesignator node) {
		traceup("ICASTDesignator ");
		return super.visit(node);
	}

	@Override
	public int leave(ICASTDesignator node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTBaseSpecifier node) {
		traceup("ICPPASTBaseSpecifier:");
		tracename(node.getName());
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTBaseSpecifier node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTCapture node) {
		traceup("ICPPASTCapture ");
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTCapture node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTTemplateParameter node) {
		traceup("ICPPASTTemplateParameter ");
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTTemplateParameter node) {
		tracedown();
		return super.leave(node);
	}

	@SuppressWarnings("restriction")
	@Override
	public int visit(ASTAmbiguousNode node) {
		tracemsg("ASTAmbiguousNode ");
		return super.visit(node);
	}

	@Override
	public int visit(IASTAttribute node) {
		traceup("IASTAttribute: "+node.getName());
		return super.visit(node);
	}

	@Override
	public int leave(IASTAttribute node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTAttributeSpecifier node) {
		traceup("IASTAttributeSpecifier ");
		return super.visit(node);
	}

	@Override
	public int leave(IASTAttributeSpecifier node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTComment node) {
		traceup("IASTComment "+node.toString());
		return super.visit(node);
	}

	@Override
	public int leave(IASTComment node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(IASTToken node) {
		traceup("IASTToken type="+node.getTokenType());
		return super.visit(node);
	}

	@Override
	public int leave(IASTToken node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTClassVirtSpecifier node) {
		traceup("ICPPASTClassVirtSpecifier ");
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTClassVirtSpecifier node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTDecltypeSpecifier node) {
		traceup("ICPPASTDecltypeSpecifier ");
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTDecltypeSpecifier node) {
		tracedown();
		return super.leave(node);
	}

	@Override
	public int visit(ICPPASTVirtSpecifier node) {
		traceup("ICPPASTVirtSpecifier ");
		return super.visit(node);
	}

	@Override
	public int leave(ICPPASTVirtSpecifier node) {
		tracedown();
		return super.leave(node);
	}

	// ADDITIONAL VISITING METODS

	public void visit(IASTCompositeTypeSpecifier node) {
		IASTName nodeName = ((IASTCompositeTypeSpecifier)node).getName();
		IBinding bnd = nodeName.resolveBinding();

		if ( (bnd != null) && (bnd instanceof ICPPClassType) ) {
//			tracemsg("    -> IASTCompositeTypeSpecifier (and a class)");

			eu.synectique.verveine.core.gen.famix.Class fmx = dico.ensureFamixClass(bnd, nodeName.toString(), /*owner*/context.top(), /*persistIt*/true);
			if (fmx != null) {
				fmx.setIsStub(false);
				
				this.context.pushType(fmx);
				// dico.addSourceAnchor(fmx, node, /*oneLineAnchor*/false);
				// if (dico.createFamixComment(node.getJavadoc(), fmx, source) == null) {
			}
		}
	}

	public void leave(IASTCompositeTypeSpecifier node) {
		IASTName nodeName = ((IASTCompositeTypeSpecifier)node).getName();
		IBinding bnd = nodeName.resolveBinding();

		if ( (bnd != null) && (bnd instanceof ICPPClassType) && (context.topType().getName().equals(nodeName.toString())) ) {
			this.context.popType();
		}
	}

	public int visit(ICPPASTFunctionDefinition node) {
		traceup("CPPASTFunctionDefinition ");
		return PROCESS_SKIP;
	
	}

	public int leave(ICPPASTFunctionDefinition node) {
		tracedown("CPPASTFunctionDefinition ");
		return PROCESS_SKIP;
	}
	
	public void visit(IASTFunctionDeclarator node) {
		IASTFunctionDeclarator func = (IASTFunctionDeclarator)node;
		IASTName nodeName = func.getName();
		IBinding bnd = nodeName.resolveBinding();
		BehaviouralEntity fmx;

//		tracemsg("    -> IASTFunctionDeclarator");

		if (bnd != null) {
			boolean iscpp = (bnd instanceof ICPPMethod);
			
			if (iscpp) {
				fmx = dico.ensureFamixMethod(bnd, nodeName.toString(), /*signature*/nodeName.toString()+"(", /*ret.type*/null, context.topType(), /*persitIt*/true);
			}
			else {
				fmx = dico.ensureFamixFunction(bnd, nodeName.toString(), /*signature*/nodeName.toString()+"(", /*ret.type*/null, context.top(), /*persitIt*/true);				
			}

			if (fmx != null) {
				fmx.setIsStub(false);
				if (iscpp) {
					this.context.pushMethod((Method) fmx);
					if (bnd instanceof ICPPConstructor) {
						((Method)fmx).setKind(Dictionary.CONSTRUCTOR_KIND_MARKER);
					}
				}
			}
		}
	}
	
	protected void leave(IASTFunctionDeclarator node) {
		if ( context.top().getName().equals(node.getName().toString()) ) {
			BehaviouralEntity fmx = context.popMethod();
			fmx.setSignature(fmx.getSignature()+")");
		}
	}

}
