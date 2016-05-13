package eu.synectique.verveine.extractor.visitors;

import java.util.Stack;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTParameterDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.c.ICASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTConstructorChainInitializer;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTConstructorInitializer;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDeclarator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNewExpression;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.model.ICContainer;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICElementVisitor;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IInclude;
import org.eclipse.cdt.core.model.IParent;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTNewExpression;
import org.eclipse.core.runtime.CoreException;

import eu.synectique.verveine.core.EntityStack;
import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.ContainerEntity;
import eu.synectique.verveine.core.gen.famix.Function;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.NamedEntity;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.TypeAlias;
import eu.synectique.verveine.core.gen.moose.MooseModel;
import eu.synectique.verveine.extractor.utils.ITracer;
import eu.synectique.verveine.extractor.utils.NullTracer;
import eu.synectique.verveine.extractor.utils.StubBinding;

/**
 * The superclass of all visitors. These visitors visit an AST to create FAMIX entities.<BR>
 * An important function of this abstract class is to dispatch more finely the visits than what CDT normally do. 
 * This visitor also merges two APIs: visit methods on AST (ASTVisitor) and visit methods on ICElements (ICElementVisitor).
 */
public abstract class AbstractVisitor extends ASTVisitor implements ICElementVisitor {

	/** 
	 * A dictionary allowing to recover created FAMIX Entities
	 */
	protected CDictionary dico;

	/**
	 * A stack that keeps the current definition context (package/class/method)
	 */
	protected EntityStack context;

	protected ITracer tracer = new NullTracer();  // no tracing by default

	/** name of the current file (TranslationUnit) being visited
	 */
	protected String filename;

	/**
	 * CDT index to resolve symbols
	 */
	protected IIndex index;

	/**
	 * A variable used in many visit methods to store the name of the current node
	 */
	protected IASTName nodeName;

	/**
	 * A variable used in many visit methods to store the binding of the current node
	 */
	protected IIndexBinding bnd;

	// CONSTRUCTOR ==========================================================================================================================

	protected AbstractVisitor(CDictionary dico) {
		this(dico, /*index*/null, /*visitNodes*/true);
	    this.dico = dico;
	}

	public AbstractVisitor(CDictionary dico, IIndex index) {
		this(dico, index, /*visitNodes*/true);
	}

	public AbstractVisitor(CDictionary dico, IIndex index, boolean visitNodes) {
		super(visitNodes);
	    /* fine-tuning if visitNodes=false
	    shouldVisitDeclarations = true;
	    shouldVisitEnumerators = true;
	    shouldVisitProblems = true;
	    shouldVisitTranslationUnit = true;
	    shouldVisit... */
		this.index = index;
	    this.dico = dico;
	}


	// VISITING METODS ON ICELEMENT HIERARCHY ==============================================================================================

	@Override
	public boolean visit(ICElement elt) {
		switch (elt.getElementType()) {
		case ICElement.C_PROJECT:
			visit( (ICProject) elt);
			break;
		case ICElement.C_CCONTAINER:
			visit( (ICContainer) elt);
			break;
		case ICElement.C_UNIT:
			visit( (ITranslationUnit) elt);
			break;
		case ICElement.C_INCLUDE:
			visit( (IInclude) elt);
			break;

		default:
			//  don't know what it is, don't know what to do with it
		}
		return false;
	}

	public void visit(ICProject project) {
		visitChildren(project);
	}

	public void visit(ICContainer cont) {
		visitChildren(cont);
	}

	public void visit(IInclude project) {
	}

	public void visit(ITranslationUnit elt) {
		try {
			visitChildren(elt);

			this.filename = elt.getFile().getRawLocation().toString();
			elt.getAST(index, ITranslationUnit.AST_CONFIGURE_USING_SOURCE_CONTEXT | ITranslationUnit.AST_SKIP_INDEXED_HEADERS).accept(this);
			this.filename = null;
		} catch (CoreException e) {
			System.err.println("*** Got CoreException (\""+ e.getMessage() +"\") while getting AST of "+ elt.getElementName() );
		}
	}


	// CDT VISITING METODS ON AST ==========================================================================================================

	@Override
	public int visit(IASTDeclaration node) {
		if (node instanceof IASTSimpleDeclaration) {
			return visit((IASTSimpleDeclaration)node);
		}
		else if (node instanceof ICPPASTFunctionDefinition) {
			return visit((ICPPASTFunctionDefinition)node);
		}
		//else ICPPASTUsingDirective, ...

		return super.visit(node);
	}

	@Override
	public int leave(IASTDeclaration node) {
		if (node instanceof IASTSimpleDeclaration) {
			return leave((IASTSimpleDeclaration)node);
		}
		else if (node instanceof ICPPASTFunctionDefinition) {
			return leave((ICPPASTFunctionDefinition)node);
		}
		//else ICPPASTUsingDirective, ...

		return super.leave(node);
	}

	@Override
	public int visit(IASTDeclarator node) {
		/* ********************************************************************************************
		 * BE CAREFULL: The order of the tests is important because:
		 * ICPPASTFunctionDeclarator is a sub-interface of IASTFunctionDeclarator
		 * IASTFunctionDeclarator is a sub-interface of ICPPASTDeclarator
		 * ******************************************************************************************** */
		if (node instanceof ICPPASTFunctionDeclarator) {
			return this.visit((ICPPASTFunctionDeclarator)node);
		}
		else if (node instanceof IASTFunctionDeclarator) {
			return this.visit((IASTFunctionDeclarator)node);
		}
		else if (node instanceof ICPPASTDeclarator) {
			return this.visit((ICPPASTDeclarator)node);
		}

		return super.visit(node);
	}

	@Override
	public int leave(IASTDeclarator node) {
		/* ********************************************************************************************
		 * BE CAREFULL: The order of the tests is important because:
		 * ICPPASTFunctionDeclarator is a sub-interface of IASTFunctionDeclarator
		 * IASTFunctionDeclarator is a sub-interface of ICPPASTDeclarator
		 * ******************************************************************************************** */
		if (node instanceof ICPPASTFunctionDeclarator) {
			return this.leave((ICPPASTFunctionDeclarator)node);
		}
		else if (node instanceof IASTFunctionDeclarator) {
			return this.leave((IASTFunctionDeclarator)node);
		}
		else if (node instanceof ICPPASTDeclarator) {
			return this.leave((ICPPASTDeclarator)node);
		}

		if (node instanceof IASTFunctionDeclarator) {
			return this.leave((IASTFunctionDeclarator)node);
		}
		return super.leave(node);
	}

	@Override
	public int visit(IASTDeclSpecifier node) {
		if (node instanceof ICASTCompositeTypeSpecifier) {
			// -> struct/union
			return this.visit((ICASTCompositeTypeSpecifier)node);
		}
		else if (node instanceof ICPPASTCompositeTypeSpecifier) {
			// -> class
			return this.visit((ICPPASTCompositeTypeSpecifier)node);
		}
		else if (node instanceof IASTEnumerationSpecifier) {
			// -> enum
			return this.visit((IASTEnumerationSpecifier)node);
		}
		else if (node instanceof ICPPASTNamedTypeSpecifier) {
			// -> typedef
			return this.visit((ICPPASTNamedTypeSpecifier)node);
		}

		return super.visit(node);
	}

	@Override
	public int leave(IASTDeclSpecifier node) {
		if (node instanceof ICASTCompositeTypeSpecifier) {
			// -> struct/union
			return this.leave((ICASTCompositeTypeSpecifier)node);
		}
		else if (node instanceof ICPPASTCompositeTypeSpecifier) {
			// -> class
			return this.leave((ICPPASTCompositeTypeSpecifier)node);
		}
		else if (node instanceof IASTEnumerationSpecifier) {
			// -> enum
			return this.leave((IASTEnumerationSpecifier)node);
		}
		else if (node instanceof ICPPASTNamedTypeSpecifier) {
			// -> typedef
			return this.leave((ICPPASTNamedTypeSpecifier)node);
		}

		return super.leave(node);
	}

	@Override
	public int visit(IASTInitializer node) {
		if (node instanceof ICPPASTConstructorChainInitializer) {
			return visit( (ICPPASTConstructorChainInitializer)node );
		}
		else if (node instanceof ICPPASTConstructorInitializer) {
			return visit( (ICPPASTConstructorInitializer)node );
		}
		return super.visit(node);
	}

	@Override
	public int visit(IASTExpression node) {
		if (node instanceof IASTFieldReference) {
			return visit((IASTFieldReference)node);
		}
		else if (node instanceof IASTIdExpression) {
			return visit((IASTIdExpression)node);
		}
		else if (node instanceof ICPPASTNewExpression) {
			return visit((ICPPASTNewExpression)node);
		}
		else if (node instanceof IASTFunctionCallExpression) {
			return visit((IASTFunctionCallExpression)node);
		}
		else if (node instanceof IASTBinaryExpression) {
			return visit((IASTBinaryExpression)node);   // to check whether this is an assignement
		}
		else if (node instanceof IASTLiteralExpression) {
			return visit((IASTLiteralExpression)node);
		}

		return super.visit(node);
	}
	

	// ADDITIONAL VISITING METODS ON AST =======================================================================================================

	protected int visit(IASTSimpleDeclaration node) {
		if (node.getDeclSpecifier().getStorageClass() == IASTDeclSpecifier.sc_typedef) {
			// this is a typedef, so define a FamixType with the declarator(s)
			for (IASTDeclarator declarator : node.getDeclarators()) {
				TypeAlias fmx = null;

				nodeName = declarator.getName();

				tracer.msg("IASTSimpleDeclaration (typedef):"+nodeName.toString());

				try {
					bnd = index.findBinding(nodeName);
				} catch (CoreException e) {
					e.printStackTrace();
				}

				if (bnd == null) {
					// create one anyway, assume this is a function
					bnd = StubBinding.getInstance(Function.class, dico.mooseName(getTopCppNamespace(), nodeName.toString()));
				}

				handleTypedef(node);
			}
			
			return PROCESS_SKIP;  // typedef already handled
		}
		return PROCESS_CONTINUE;
	}

	/**
	 * Call back method from {@link visit(IASTSimpleDeclaration)}
	 * Treated differently than other visit methods because, although unlikely, there could be more than one AliasType in the same typedef
	 * thus several nodeName and bnd.
	 */
	protected void handleTypedef(IASTSimpleDeclaration node) { }

	protected int leave(IASTSimpleDeclaration node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTFunctionDefinition node) {
		return PROCESS_CONTINUE;
	}

	protected int leave(ICPPASTFunctionDefinition node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTFunctionDeclarator node) {

		bnd = null;
		nodeName = node.getName();
		tracer.msg("ICPPASTFunctionDeclarator: "+nodeName);

		try {
			bnd = index.findBinding(nodeName);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		if (bnd == null) {
			// create one anyway, assume this is a function
			bnd = StubBinding.getInstance(Function.class, dico.mooseName(getTopCppNamespace(), nodeName.toString()));
		}

		return PROCESS_CONTINUE;
	}

	protected int leave(ICPPASTFunctionDeclarator node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTFunctionDeclarator node) {
		return PROCESS_CONTINUE;
	}

	protected int leave(IASTFunctionDeclarator node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTDeclarator node) {
		bnd = null;
		nodeName = null;

		if ( (node.getParent() instanceof IASTSimpleDeclaration) &&
			 (node.getParent().getParent() instanceof IASTCompositeTypeSpecifier) &&
			 ( ((IASTSimpleDeclaration)node.getParent()).getDeclSpecifier().getStorageClass() != IASTDeclSpecifier.sc_typedef)  ) {
			// this is an Attribute declaration, get it back
			nodeName = node.getName();

			try {
				bnd = index.findBinding(nodeName);
			} catch (CoreException e) {
				e.printStackTrace();
			}

			if (bnd == null) {
				bnd = StubBinding.getInstance(Attribute.class, dico.mooseName(context.topType(), nodeName.toString()));
			}
		}

		return PROCESS_CONTINUE;
	}

	protected int leave(ICPPASTDeclarator node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICASTCompositeTypeSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int leave(ICASTCompositeTypeSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTCompositeTypeSpecifier node) {
		bnd = null;
		nodeName = node.getName();

		if (nodeName == null) {
			tracer.msg("ICPPASTCompositeTypeSpecifier without name");
			return PROCESS_SKIP;
		}

		tracer.up("ICPPASTCompositeTypeSpecifier:"+nodeName.toString());

		try {
			bnd = index.findBinding(nodeName);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		if (bnd == null) {
			// create one anyway
			if (fullyQualified(nodeName)) {
				Namespace parent = createParentNamespace(nodeName);
				bnd = StubBinding.getInstance(eu.synectique.verveine.core.gen.famix.Class.class, dico.mooseName(parent, simpleName(nodeName)));
			}
			else {
				bnd = StubBinding.getInstance(eu.synectique.verveine.core.gen.famix.Class.class, dico.mooseName(getTopCppNamespace(), nodeName.toString()));
			}
		}

		return PROCESS_CONTINUE;
	}

	protected int leave(ICPPASTCompositeTypeSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTEnumerationSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int leave(IASTEnumerationSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTNamedTypeSpecifier node) {
		return PROCESS_CONTINUE;
	}

	protected int leave(ICPPASTNamedTypeSpecifier node) {
		return PROCESS_CONTINUE;
	}

	public int visit(IASTParameterDeclaration node) {
		return PROCESS_CONTINUE;
	}

	public int leave(IASTParameterDeclaration node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTFunctionCallExpression node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTNewExpression node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTBinaryExpression node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTLiteralExpression node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTFieldReference node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(IASTIdExpression node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTConstructorChainInitializer node) {
		return PROCESS_CONTINUE;
	}

	protected int visit(ICPPASTConstructorInitializer node) {
		return PROCESS_CONTINUE;
	}

	// UTILITIES ======================================================================================================

	private void visitChildren(IParent elt) {
		try {
			for (ICElement child : elt.getChildren()) {
				child.accept(this);
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	protected boolean fullyQualified(IASTName name) {
		return fullyQualified(name.toString());
	}

	protected boolean fullyQualified(String name) {
		return name.indexOf(CDictionary.CPP_NAME_SEPARATOR) > 0;
	}

	/**
	 * Returns the last part of a fully qualified name
	 */
	protected String simpleName(IASTName name) {
		
		return simpleName(name.toString());
	}

	protected String simpleName(String name) {
		String str = name.toString(); 
		int i = str.lastIndexOf(CDictionary.CPP_NAME_SEPARATOR);
		
		return str.substring(i+2);
	}

	/**
	 * Creates recursively namespaces from a fully qualified name.
	 * The last member of the name is not considered (i.e. a::b::c will yield Namespaces a and a::b)
	 */
	protected Namespace createParentNamespace(IASTName name) {
		return createParentNamespace(name.toString());
	}

	protected Namespace createParentNamespace(String name) {
		int i;
		String str;
		str = name.toString();
		i = str.lastIndexOf(CDictionary.CPP_NAME_SEPARATOR);
		
		if (i > 0) {
			return createNamespace(str.substring(0, i));
		}
		else {
			return null;
		}
	}

	/**
	 * Creates recursively namespaces from a fully qualified name.
	 * Assumes the fully qualified name is referent to the root of namespace and not within the current namespace
	 * This means we do not consider the context
	 */
	protected Namespace createNamespace(String name) {
		int i;
		String namespaceName;
		Namespace parent=null;
		StubBinding bnd;
		
		i = name.lastIndexOf(CDictionary.CPP_NAME_SEPARATOR);
		if (i > 0) {
			namespaceName = name.substring(i+2);  // could also use simpleName(name)
			parent = createNamespace(name.substring(0, i));
		}
		else {
			namespaceName = name;
		}

		bnd = StubBinding.getInstance(Namespace.class, dico.mooseName(parent, namespaceName));
		
		return dico.ensureFamixNamespace(bnd, namespaceName, parent);
	}

	/**
	 * Returns the higher-most namespace in the C++ sense on the EntityStack
	 * C++ namespaces we are interested in are: methods, classes, namespaces
	 * @return null if could not find a C++ namespace
	 */
	protected ContainerEntity getTopCppNamespace() {
		Stack<NamedEntity> tmp = new Stack<NamedEntity>();
		NamedEntity top;
		
		top = context.pop();
		tmp.push(top);
		while ( ! ((top == null) ||
				   (top instanceof Method) ||
				   (top instanceof eu.synectique.verveine.core.gen.famix.Class) ||
				   (top instanceof Namespace) )) {
			top = context.pop();
			tmp.push(top);
		}
		
		while (! tmp.empty()) {
			context.push( tmp.pop());
		}

		return (ContainerEntity) top;
	}

}
