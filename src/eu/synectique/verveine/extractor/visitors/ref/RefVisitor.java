package eu.synectique.verveine.extractor.visitors.ref;

import java.io.RandomAccessFile;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTConstructorChainInitializer;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTConstructorInitializer;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDeclarator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamespaceDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTemplateDeclaration;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBase;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.model.ICElementVisitor;
import org.eclipse.cdt.core.model.ITranslationUnit;

import eu.synectique.verveine.core.Dictionary;
import eu.synectique.verveine.core.gen.famix.Access;
import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.Class;
import eu.synectique.verveine.core.gen.famix.Inheritance;
import eu.synectique.verveine.core.gen.famix.NamedEntity;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.Parameter;
import eu.synectique.verveine.core.gen.famix.StructuralEntity;
import eu.synectique.verveine.core.gen.famix.Type;
import eu.synectique.verveine.core.gen.famix.TypeAlias;
import eu.synectique.verveine.extractor.plugin.CDictionary;
import eu.synectique.verveine.extractor.utils.NullTracer;
//import eu.synectique.verveine.extractor.utils.Tracer;
import eu.synectique.verveine.extractor.utils.StubBinding;
import eu.synectique.verveine.extractor.utils.SubVisitorFactory;
import eu.synectique.verveine.extractor.visitors.AbstractVisitor;
import eu.synectique.verveine.extractor.visitors.SignatureBuilderVisitor;

public class RefVisitor extends AbstractRefVisitor implements ICElementVisitor {

	/**
	 * The source code of the visited AST.
	 * Used to find back the contents of non-javadoc comments
	 */
	protected RandomAccessFile source;

	/**
	 * Whether a variable access is lhs (write) or not
	 */
	protected boolean inAssignmentLHS = false;

	// CONSTRUCTOR ==========================================================================================================================

	/**
	 * Constructor for "main" visitor, dicoDef contains entities created during def pass
	 * @param dicoDef contains entities created during def pass
	 * @param dicoRef where entities are created during ref pass (the current pass)
	 * @param index CDT index containing bindings
	 */
	public RefVisitor(CDictionary dico, IIndex index) {
		super(dico, index);

		tracer = new NullTracer("REF>");
	}

	// VISITING METODS ON ICELEMENT HIERARCHY ==============================================================================================

	/*
	 * redefining to check for header files (or not)
	 */
	@Override
	public void visit(ITranslationUnit elt) {
		if (checkHeader(elt)) {
			super.visit(elt);
		}
	}

	// CDT VISITING METODS ON AST ==========================================================================================================

	@Override
	public int visit(IASTParameterDeclaration node) {
		Parameter fmx = null;
		String paramName = null;
		BehaviouralEntity parent = null;

		 // get node name and bnd
		if (super.visit(node) == PROCESS_SKIP) {
			return PROCESS_SKIP;
		}
		fmx = (Parameter) dico.getEntityByKey(nodeBnd);

		if (fmx == null) {
			/* get param name and parent */
			parent = context.topBehaviouralEntity();
			paramName = nodeName.toString();

			/* last try to recover parameter */
			fmx = (Parameter) findInParent(paramName, parent, false);
		}

		if (fmx == null) {
			// should really really not happen
			fmx = dico.ensureFamixParameter(nodeBnd, paramName, parent);
		}

		// now get the declared type
		if (fmx != null) {
			if (node.getParent() instanceof ICPPASTTemplateDeclaration) {
				// parameterType in a template
				// ignore for now
			}
			else {
				fmx.setDeclaredType( referedType( node.getDeclSpecifier() ) );
			}
		}
		returnedEntity = fmx;

		return PROCESS_SKIP;
	}


	// ADDITIONAL VISITING METODS ON AST ==================================================================================================

	@Override
	protected int visit(IASTSimpleDeclaration node) {
		// compute nodeName and binding
		// also handles typedefs (see handleTypedef)
		// here we are interested in functions/methods declarations
		// to get the return type of the function

		if (declarationIsTypedef(node)) {
			for (IASTDeclarator declarator : node.getDeclarators()) {
			// this is a typedef, so the declarator(s) should be FAMIXType(s)

				nodeName = declarator.getName();

				tracer.msg("IASTSimpleDeclaration (typedef):"+nodeName.toString());

				nodeBnd = getBinding(nodeName);

				if (nodeBnd == null) {
					// create one anyway, assume this is a Type
					nodeBnd = StubBinding.getInstance(Type.class, dico.mooseName(context.getTopCppNamespace(), nodeName.toString()));
				}

				/* Call back method.
				 * Treated differently than other visit methods because, although unlikely, there could be more than one AliasType in the same typedef
				 * thus several nodeName and bnd
				 */
				visitSimpleTypeDeclaration(node);
			}
			
			return PROCESS_SKIP;  // typedef already handled
		}

		if (node.getDeclarators().length > 0) {
			for (IASTDeclarator declarator : node.getDeclarators()) {
				returnedEntity = null;
				this.visit( declarator );

				if (returnedEntity instanceof BehaviouralEntity) {
					((BehaviouralEntity)returnedEntity).setDeclaredType( referedType( node.getDeclSpecifier() ) );
				}
				else if (returnedEntity instanceof StructuralEntity) {
					((StructuralEntity)returnedEntity).setDeclaredType( referedType( node.getDeclSpecifier() ) );
				}
			}
			return PROCESS_SKIP;
		}

		return PROCESS_CONTINUE;
	}

	/*
	 * Visiting a class definition, need to put it on the context stack to create its members
	 */
	@Override
	protected int visit(ICPPASTCompositeTypeSpecifier node) {
		eu.synectique.verveine.core.gen.famix.Class fmx;

		// compute nodeName and binding
		super.visit(node);

		fmx = (eu.synectique.verveine.core.gen.famix.Class) dico.getEntityByKey(nodeBnd);

		this.context.push(fmx);

		for (IASTDeclaration child : node.getDeclarations(/*includeInactive*/false)) {
			child.accept(this);
		}

		returnedEntity = this.context.pop();

		return PROCESS_SKIP;
	}

	/**
	 * Call back method from {@link #visit(IASTSimpleDeclaration)}
	 * sets the referedType for the AliasType of the typedef.
	 * Treated differently than other visit methods because, although unlikely, there could be more than one AliasType in the same typedef
	 * thus several nodeName and bnd.
	 */
	protected void visitSimpleTypeDeclaration(IASTSimpleDeclaration node) {
		TypeAlias fmx;

		fmx = (TypeAlias) dico.getEntityByKey(nodeBnd);

		fmx.setAliasedType( referedType( node.getDeclSpecifier() ) );

		returnedEntity = fmx;
	}

	/**
	 * Visiting an attribute just to get it.
	 * {@link #visit(IASTSimpleDeclaration)} takes care of setting the return type
	 */
	@Override
	protected int visit(ICPPASTDeclarator node) {

		nodeBnd = null;
		nodeName = null;

		if (node.getParent() instanceof IASTSimpleDeclaration) {
			if ( nodeParentIsClass(node.getParent()) && ! declarationIsTypedef((IASTSimpleDeclaration)node.getParent()) ) {
				// this is an Attribute declaration, get it back
				nodeName = node.getName();

				nodeBnd = getBinding(nodeName);
				if (nodeBnd == null) {
					nodeBnd = StubBinding.getInstance(Attribute.class, dico.mooseName(context.topType(), nodeName.toString()));
				}
			}
		}


		if (node.getInitializer() != null ) {
			node.getInitializer().accept(this);
		}


		if (nodeBnd != null) {
			returnedEntity = (Attribute) dico.getEntityByKey(nodeBnd);
		}

		return PROCESS_SKIP;
	}

	/**
	 * Visiting a method or function declaration
	 */
	@Override
	protected int visit(ICPPASTFunctionDeclarator node) {
		BehaviouralEntity fmx = null;

		// compute nodeName and binding
		super.visit(node);

		fmx = (BehaviouralEntity) dico.getEntityByKey(nodeBnd);
		// try harder
		if (fmx == null) {
			fmx = resolveBehaviouralFromName(node, nodeBnd);
		}

		this.context.push(fmx);
		for (ICPPASTParameterDeclaration param : node.getParameters()) {
			param.accept(this);
		}
		returnedEntity = this.context.pop();

		return PROCESS_SKIP;  // already visited all we needed
	}

	/**
	 * Visiting a method or function definition
	 */
	@Override
	protected int visit(ICPPASTFunctionDefinition node) {
		BehaviouralEntity fmx = null;


		// visit declarator to ensure the method definition and to get the Famix entity
		returnedEntity = null;
		this.visit( (ICPPASTFunctionDeclarator)node.getDeclarator() );

		fmx = (BehaviouralEntity) returnedEntity;
		if (fmx != null) {
			this.context.push((NamedEntity) returnedEntity);

			for (ICPPASTConstructorChainInitializer init : node.getMemberInitializers()) {
				init.accept(this);
			}

			// return type of the Behavioural
			fmx.setDeclaredType( referedType( node.getDeclSpecifier() ) );

			// body to compute some metrics
			node.getBody().accept(this);
			
			returnedEntity = context.pop();
		}

		return PROCESS_SKIP;  // we already visited the children
	}

	@Override
	protected int visit(ICPPASTConstructorChainInitializer node) {
		FunctionCallVisitor subVisitor = SubVisitorFactory.createSubVisitorFCV(this);
		int retVal = subVisitor.visit(node);
		returnedEntity = subVisitor.returnedEntity();
		return retVal;
	}

	@Override
	protected int visit(ICPPASTConstructorInitializer node) {
		FunctionCallVisitor subVisitor = SubVisitorFactory.createSubVisitorFCV(this);
		int retVal = subVisitor.visit(node);
		returnedEntity = subVisitor.returnedEntity();
		return retVal;
	}

	@Override
	protected int visit(IASTFunctionCallExpression node) {
		FunctionCallVisitor subVisitor = SubVisitorFactory.createSubVisitorFCV(this);
		int retVal = subVisitor.visit(node);
		returnedEntity = subVisitor.returnedEntity();
		return retVal;
	}

	@Override
	protected int visit(IASTLiteralExpression node) {
		returnedEntity = null;
		if ( ((IASTLiteralExpression)node).getKind() == ICPPASTLiteralExpression.lk_this ) {
			if (context.topType() != null) {
				returnedEntity = accessToVar(dico.ensureFamixImplicitVariable(Dictionary.SELF_NAME, /*type*/context.topType(), /*owner*/context.topBehaviouralEntity()));
			}
			else if (context.topMethod() != null) {
				returnedEntity = accessToVar(dico.ensureFamixImplicitVariable(Dictionary.SELF_NAME, /*type*/context.topMethod().getParentType(), /*owner*/context.topBehaviouralEntity()));
			}
		}
		return PROCESS_SKIP;
	}

	@Override
	protected int visit(IASTFieldReference node) {
		// can also be a method invocation
		boolean reference;

		((IASTFieldReference)node).getFieldOwner().accept(this);
		
		reference = ( (node.getParent() instanceof ICPPASTUnaryExpression) &&
					  ( ((ICPPASTUnaryExpression)node.getParent()).getOperator() == ICPPASTUnaryExpression.op_amper) );
		returnedEntity = referenceToName(((IASTFieldReference) node).getFieldName(), reference);

		return PROCESS_SKIP;
	}

	@Override
	protected int visit(IASTIdExpression node) {
		boolean isPointer;
		isPointer = ( (node.getParent() instanceof ICPPASTUnaryExpression) &&
					  ( ((ICPPASTUnaryExpression)node.getParent()).getOperator() == ICPPASTUnaryExpression.op_amper) );
		returnedEntity = referenceToName(((IASTIdExpression) node).getName(), isPointer);
		return PROCESS_SKIP;
	}

	@Override
	protected int visit(IASTUnaryExpression node) {
		node.getOperand().accept(this);
		return PROCESS_SKIP;
	}

	public int visit(IASTBinaryExpression node) {
		node.getOperand1().accept(this);

		switch (node.getOperator()) {
		case IASTBinaryExpression.op_assign:
		case IASTBinaryExpression.op_binaryAndAssign:
		case IASTBinaryExpression.op_binaryOrAssign:
		case IASTBinaryExpression.op_binaryXorAssign:
		case IASTBinaryExpression.op_divideAssign:
		case IASTBinaryExpression.op_minusAssign:
		case IASTBinaryExpression.op_moduloAssign:
		case IASTBinaryExpression.op_multiplyAssign:
		case IASTBinaryExpression.op_plusAssign:
		case IASTBinaryExpression.op_shiftLeftAssign:
		case IASTBinaryExpression.op_shiftRightAssign:
			if (this.returnedEntity() instanceof Access) {
				((Access) this.returnedEntity()).setIsWrite(true);
			}
		}
		node.getOperand2().accept(this);
		
		return PROCESS_SKIP;
	}


	
}
