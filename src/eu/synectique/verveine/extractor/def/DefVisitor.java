package eu.synectique.verveine.extractor.def;

import org.eclipse.cdt.core.model.CModelException;
import org.eclipse.cdt.core.model.ICContainer;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICElementVisitor;
import org.eclipse.cdt.core.model.IEnumeration;
import org.eclipse.cdt.core.model.IField;
import org.eclipse.cdt.core.model.IFunction;
import org.eclipse.cdt.core.model.IFunctionDeclaration;
import org.eclipse.cdt.core.model.IMethod;
import org.eclipse.cdt.core.model.IMethodDeclaration;
import org.eclipse.cdt.core.model.INamespace;
import org.eclipse.cdt.core.model.IParent;
import org.eclipse.cdt.core.model.IStructure;
import org.eclipse.cdt.core.model.IStructureDeclaration;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.core.model.ITypeDef;
import org.eclipse.cdt.core.model.IVariable;
import org.eclipse.cdt.core.model.IVariableDeclaration;
import org.eclipse.core.runtime.CoreException;

import eu.synectique.verveine.core.Dictionary;
import eu.synectique.verveine.core.EntityStack2;
import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.ContainerEntity;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.ScopingEntity;
import eu.synectique.verveine.core.gen.famix.Type;
import eu.synectique.verveine.extractor.utils.ITracer;
import eu.synectique.verveine.extractor.utils.NullTracer;
import eu.synectique.verveine.extractor.utils.Tracer;

/**
 * Not a Visitor  on an AST, visits the tree of ICElement and defines all entities
 * These entities will be needed by the RefVisitor
 */
public class DefVisitor implements ICElementVisitor {

	/** 
	 * The dictionary that creates FAMIX Entities and hold the FAMIX repository
	 */
	protected CDictionaryDef dico;

	/**
	 * A stack that keeps the current definition context (package/class/method)
	 */
	protected EntityStack2 context;

	protected eu.synectique.verveine.core.gen.famix.Package currentPackage;

	protected String currentFile;
	
	protected ITracer tracer;

	public DefVisitor(CDictionaryDef dico) {
	    this.dico = dico;
		this.context = new EntityStack2();
		tracer = new NullTracer("#DEF ");
	}

	// VISITING METODS ON ICELEMENT HIERARCHY ======================================================================================================
	
	@Override
	public boolean visit(ICElement elt) {
		tracer.up("ICElement:"+elt.getElementName()+"    child of:"+elt.getParent().getElementName());
		switch (elt.getElementType()) {
		case ICElement.C_PROJECT:
			tracer.down();
			return true; // visit children and we don't care about knowing when these visits end
		case ICElement.C_CCONTAINER:
			visit( (ICContainer) elt);
			break;
		case ICElement.C_UNIT:
			visit( (ITranslationUnit) elt);
			break;
		case ICElement.C_NAMESPACE:
			visit( (INamespace) elt);
			break;
		case ICElement.C_ENUMERATION:
			visit( (IEnumeration) elt);
			break;
		case ICElement.C_CLASS_DECLARATION:
		case ICElement.C_STRUCT_DECLARATION:
		case ICElement.C_UNION_DECLARATION:
		case ICElement.C_CLASS:
		case ICElement.C_UNION:
		case ICElement.C_STRUCT:
		case ICElement.C_TEMPLATE_CLASS_DECLARATION:
		case ICElement.C_TEMPLATE_STRUCT_DECLARATION:
		case ICElement.C_TEMPLATE_UNION_DECLARATION:
		case ICElement.C_TEMPLATE_CLASS:
		case ICElement.C_TEMPLATE_STRUCT:
		case ICElement.C_TEMPLATE_UNION:
			visit( (IStructure) elt);
			break;
		case ICElement.C_METHOD_DECLARATION:
		case ICElement.C_FUNCTION_DECLARATION:
		case ICElement.C_METHOD:
		case ICElement.C_FUNCTION:
		case ICElement.C_TEMPLATE_METHOD:
		case ICElement.C_TEMPLATE_FUNCTION:
		case ICElement.C_TEMPLATE_METHOD_DECLARATION:
		case ICElement.C_TEMPLATE_FUNCTION_DECLARATION:
			visit( (IFunctionDeclaration) elt);
			break;
		case ICElement.C_FIELD:
			visit( (IField) elt);
			break;
		case ICElement.C_VARIABLE:
		case ICElement.C_VARIABLE_DECLARATION:
			visit( (IVariableDeclaration) elt);
			break;
		case ICElement.C_TYPEDEF:
			visit( (ITypeDef) elt);
			break;
		case ICElement.C_INCLUDE:
		default:
			//  don't know what it is, don't know what to do with it
		}
		
		tracer.down("ICElement:"+elt.getElementName());
		/* 
		 * Return false in most cases so that we can handle the visiting of children of the nodes ourselves
		 * so that we can manage properly the context stack.
		 * 
		 * What's the use of CDT ICElementVisitor in this case? I am really not sure ...
		 */
		return false;
	}

	/**
	 * Visiting a directory.
	 * Interpreted as a C++ Package
	 */
	private void visit(ICContainer elt) {
		eu.synectique.verveine.core.gen.famix.Package fmx = null;
		fmx = dico.ensurePackage(elt.getElementName(), currentPackage);
		fmx.setIsStub(false);

		currentPackage = fmx;                        // kind of pushing new package on a virtual package stack
		visitChildren(elt);
		currentPackage = fmx.getParentPackage();    // kind of poping out the new package from the package stack
	}

	/**
	 * Visiting a file.
	 * Record the name of that file, needed when visiting its children
	 */
	private void visit(ITranslationUnit elt) {
		currentFile = elt.getFile().getRawLocation().toString();
		visitChildren(elt);
		currentFile = null;
	}

	/**
	 * Visiting a Namespace declaration
	 */
	private void visit(INamespace elt) {
		Namespace fmx = dico.ensureNamespace(elt.getElementName(), (ScopingEntity) this.context.top());
		fmx.setIsStub(false);
		tracer.up("Namespace:"+fmx.getName());

		this.context.push(fmx);
		visitChildren(elt);
		this.context.pop();
		
		tracer.down();
	}

	private void visit(IEnumeration elt) {
	}

	/**
	 * Visiting a class declaration
	 */
	private void visit(IStructure elt) {
		eu.synectique.verveine.core.gen.famix.Class fmx = null;

		try {
			switch (elt.getElementType()) {
			case ICElement.C_STRUCT:
			case ICElement.C_CLASS:
			case ICElement.C_STRUCT_DECLARATION:
			case ICElement.C_CLASS_DECLARATION:
				fmx = dico.ensureClass(currentFile, elt.getSourceRange(), elt.getElementName(), (ContainerEntity)context.top());
				break;
			case ICElement.C_TEMPLATE_STRUCT:
			case ICElement.C_TEMPLATE_CLASS:
			case ICElement.C_TEMPLATE_STRUCT_DECLARATION:
			case ICElement.C_TEMPLATE_CLASS_DECLARATION:
				fmx = dico.ensureParameterizableClass(currentFile, elt.getSourceRange(), elt.getElementName(), (ContainerEntity)context.top());
				break;
			}
		}
		catch (CModelException e) {
				e.printStackTrace();
		}

		fmx.setIsStub(false);
		fmx.setParentPackage(currentPackage);
		tracer.up("Class:"+fmx.getName());

		this.context.push(fmx);
		visitChildren(elt);
		this.context.pop();
		
		tracer.down();

	}

	/**
	 * Visiting a function or method declaration.
	 * This applies also to function/method definition as IMethod inherits from IMethodDeclaration
	 */
	private void visit(IFunctionDeclaration elt) {
		BehaviouralEntity fmx = null;
		try {
			switch (elt.getElementType()) {
			case ICElement.C_METHOD_DECLARATION:
			case ICElement.C_METHOD:
			case ICElement.C_TEMPLATE_METHOD_DECLARATION:
			case ICElement.C_TEMPLATE_METHOD:
				fmx = dico.ensureMethod(currentFile, elt.getSourceRange(), elt.getElementName(), elt.getSignature(), (Type)context.topType());
				if ( ((IMethodDeclaration)elt).isConstructor() ) {
					((Method)fmx).setKind(Dictionary.CONSTRUCTOR_KIND_MARKER);
				}
				if ( ((IMethodDeclaration)elt).isDestructor() ) {
					//((Method)fmx).setKind(Dictionary.DESTRUCTOR_KIND_MARKER);
				}
				break;
			case ICElement.C_FUNCTION_DECLARATION:
			case ICElement.C_FUNCTION:
			case ICElement.C_TEMPLATE_FUNCTION:
			case ICElement.C_TEMPLATE_FUNCTION_DECLARATION:
				fmx = dico.ensureFunction(currentFile, elt.getSourceRange(), elt.getElementName(), elt.getSignature(), (Type)context.topType());
				break;
			}
			
			fmx.setIsStub(false);
			tracer.msg("created BehaviouralEntity:"+fmx.getName());
		} catch (CModelException e) {
			e.printStackTrace();
		}
	}

	private void visit(IField elt) {
		Attribute fmx;
		try {
			fmx = dico.ensureAttribute(currentFile, elt.getSourceRange(), elt.getElementName(), (Type)context.topType());
			fmx.setIsStub(false);
			tracer.msg("created Field:"+fmx.getName());
		} catch (CModelException e) {
			e.printStackTrace();
		}
	}

	private void visit(IVariableDeclaration elt) {
	}

	private void visit(ITypeDef elt) {
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

}
