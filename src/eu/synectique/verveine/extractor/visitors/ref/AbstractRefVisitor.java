package eu.synectique.verveine.extractor.visitors.ref;

import org.eclipse.cdt.core.dom.ast.IASTCompositeTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTElaboratedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTEnumerationSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNameOwner;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.index.IIndex;

import eu.synectique.verveine.core.EntityStack;
import eu.synectique.verveine.core.gen.famix.Access;
import eu.synectique.verveine.core.gen.famix.Association;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.BehaviouralReference;
import eu.synectique.verveine.core.gen.famix.DereferencedInvocation;
import eu.synectique.verveine.core.gen.famix.Invocation;
import eu.synectique.verveine.core.gen.famix.NamedEntity;
import eu.synectique.verveine.core.gen.famix.SourcedEntity;
import eu.synectique.verveine.core.gen.famix.StructuralEntity;
import eu.synectique.verveine.core.gen.famix.Type;
import eu.synectique.verveine.extractor.utils.StubBinding;
import eu.synectique.verveine.extractor.visitors.AbstractVisitor;
import eu.synectique.verveine.extractor.visitors.CDictionary;

/**
 * Abstract superclass for Reference visitors.<BR>
 * It defines some utility methods to create references to names.
 * It also adds a constructor accepting another RefVisitor
 * to create sub-visitors (e.g. {@link FunctionCallVisitor}).
 * @author anquetil
 */
public abstract class AbstractRefVisitor extends AbstractVisitor {

	/**
	 * see {@link #returnedEntity}
	 */
	public SourcedEntity returnedEntity() {
		return returnedEntity;
	}

	// CONSTRUCTORS ==========================================================================================================================

	/**
	 * Constructor for the "main" RefVisitor
	 */
	public AbstractRefVisitor(CDictionary dico, IIndex index) {
		super(dico, index);
	}

	/**
	 * Constructor for sub RefVisitors.
	 * A sub visitor starts in the same state as its parent visitor
	 */
	public AbstractRefVisitor(AbstractRefVisitor parentVisitor) {
		super(parentVisitor.getDico(), parentVisitor.getIndex());
		context = parentVisitor.getContext(); 
	}

	// UTILITIES ==============================================================================================================================

	/**
	 * Dictionary getter.<BR>
	 * Only intended for subRef visitors to get the same dictionary as their parent visitor
	 */
	protected CDictionary getDico() {
		return dico;
	}

	/**
	 * context getter.<BR>
	 * Only intended for subRef visitors to get the same context as their parent visitor
	 */
	protected EntityStack getContext() {
		return context;
	}

	/**
	 * Index getter.<BR>
	 * Only intended for subRef visitors to get the same index as their parent visitor
	 */
	protected IIndex getIndex() {
		return index;
	}

	/**
	 * Records a reference to a name which can be a variable or behavioral name.
	 * @param nodeName
	 * @param reference 
	 * @return the Access or Invocation created
	 */
	protected Association referenceToName(IASTName nodeName, boolean reference) {
		IBinding bnd = null;
		NamedEntity fmx = null;

		bnd = getBinding(nodeName);

		if (bnd == null) {
			return null;
		}

		fmx = dico.getEntityByKey(bnd);

		if (fmx == null) {
			return null;
		}

		if (fmx instanceof StructuralEntity) {
			return accessToVar((StructuralEntity) fmx);
		}
		else if (fmx instanceof BehaviouralEntity) {
			if (reference) {
				behaviouralPointer((BehaviouralEntity) fmx);
			}
			else {
				return invocationOfBehavioural((BehaviouralEntity) fmx);
			}
		}

		return null;
	}

	/**
	 * Records an Invocation of a famixBehaviouralEntity and sets lastInvocation attribute.
	 * Assumes the context is correctly set (i.e. top contains a BehaviouralEntity that makes the invocation) 
	 * @param fmx -- invoked BehaviouralEntity
	 * @return the invocation created
	 */
	protected Invocation invocationOfBehavioural(BehaviouralEntity fmx) {
		BehaviouralEntity accessor = this.context.topBehaviouralEntity();
		Invocation invok = dico.addFamixInvocation(accessor, fmx, /*receiver*/null, /*signature*/null, context.getLastInvocation());
		context.setLastInvocation(invok);
		return invok;
	}

	/**
	 * Records an Invocation of a BehaviouralEntity referenced by a variable (a pointer) and sets lastInvocation attribute.
	 * Assumes the context is correctly set (i.e. top contains a BehaviouralEntity that makes the invocation) 
	 * @param fmx -- StructuralEntity pointing to a BehaviouralEntity invoked
	 * @return the invocation created
	 */
	protected DereferencedInvocation dereferencedInvocation(StructuralEntity fmx) {
		BehaviouralEntity accessor = this.context.topBehaviouralEntity();
		DereferencedInvocation invok = dico.addFamixDereferencedInvocation(accessor, fmx, /*signature*/null, context.getLastInvocation());
		context.setLastInvocation(invok);
		return invok;
	}

	/**
	 * Records a reference (pointer) to a famixBehaviouralEntity.
	 * Assumes the context is correctly set (i.e. top contains another BehaviouralEntity that makes the reference) 
	 * @param fmx -- referenced BehaviouralEntity
	 * @return the reference created
	 */
	protected BehaviouralReference behaviouralPointer(BehaviouralEntity fmx) {
		BehaviouralEntity referer = this.context.topBehaviouralEntity();
		BehaviouralReference ref = dico.addFamixBehaviouralReference(referer, fmx);
		return ref;
	}

	/**
	 * Records an Access to a StructuralEntity and sets lastAccess attribute.
	 * Assumes the context is correctly set (i.e. top contains a BehaviouralEntity that makes the s) 
	 * @param fmx -- Accessed StructuralEntity
	 * @return the Access created
	 */
	protected Access accessToVar(StructuralEntity fmx) {
		BehaviouralEntity accessor;
		// put false to isWrite by default, will be corrected in the visitor
		accessor = this.context.topBehaviouralEntity();
		Access acc = dico.addFamixAccess(accessor, fmx, /*isWrite*/false, context.getLastAccess());
		context.setLastAccess(acc);
		return acc;
	}

	/**
	 * From a declSpecifier, looks for a corresponding FamixType, creating it if needed (as a stub)
	 */
	protected Type referedType(IASTDeclSpecifier node) {
		if (node instanceof IASTSimpleDeclSpecifier) {
			return dico.ensureFamixPrimitiveType( ((IASTSimpleDeclSpecifier) node).getType());
		}
		else if (node instanceof IASTNameOwner) {
			IASTName nodeName = null;
			IBinding bnd = null;

			// all these tests to call methods with the same name in the end ...
			if  (node instanceof IASTCompositeTypeSpecifier) {
				nodeName = ((IASTCompositeTypeSpecifier) node).getName();
			}
			else if (node instanceof IASTElaboratedTypeSpecifier) {
				nodeName = ((IASTElaboratedTypeSpecifier) node).getName();
			}
			else if (node instanceof IASTEnumerationSpecifier) {
				nodeName = ((IASTEnumerationSpecifier) node).getName();
			}
			else if (node instanceof IASTNamedTypeSpecifier) {
				nodeName = ((IASTNamedTypeSpecifier) node).getName();
			}
			bnd = getBinding( nodeName);

			if (bnd == null) {
				bnd = StubBinding.getInstance(Type.class, dico.mooseName(getTopCppNamespace(), nodeName.toString()));
				//return dico.ensureFamixUniqEntity(Type.class, bnd, nodeName.toString());
			}

			// get the type or creates a stub
			return (Type) dico.ensureFamixType(bnd, nodeName.toString());
		}

		// should not happen
		return null;
	}

}