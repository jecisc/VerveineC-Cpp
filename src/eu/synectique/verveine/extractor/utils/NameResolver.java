package eu.synectique.verveine.extractor.utils;

import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPConstructor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.core.runtime.CoreException;

import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.BehaviouralEntity;
import eu.synectique.verveine.core.gen.famix.ContainerEntity;
import eu.synectique.verveine.core.gen.famix.Function;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.NamedEntity;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.ScopingEntity;
import eu.synectique.verveine.core.gen.famix.StructuralEntity;
import eu.synectique.verveine.core.gen.famix.Type;
import eu.synectique.verveine.extractor.plugin.CDictionary;
import eu.synectique.verveine.extractor.visitors.SignatureBuilderVisitor;

/**
 * A library class with several utility methods useful for name resolution.
 */
public class NameResolver {

	private CppEntityStack context;


	/**
	 * CDT index to get AST and resolve symbols
	 */
	protected IIndex index;

	/** 
	 * A dictionary allowing to store created FAMIX Entities
	 * <p>
	 * This does not strictly belongs here, but all sub-classes need it,
	 * so it's simpler to have it in their super-class (this one)
	 */
	protected CDictionary dico;

	public NameResolver(CDictionary dico, IIndex index) {
		super();
		this.dico = dico;
		this.index = index;
	}

	public CppEntityStack getContext() {
		return context;
	}

	public void setContext(CppEntityStack context) {
		this.context = context;
	}

	/**
	 * Creates a StubBinding for <code>name</code>. If <code>name</code> is unqualified, defaults to considering it in the current context
	 */
	public <T extends NamedEntity> IBinding mkStubKey(IASTName name, java.lang.Class<T> entityType) {
		return mkStubKey( name.toString(), entityType);
	}

	public <T extends NamedEntity> IBinding mkStubKey(String name, java.lang.Class<T> entityType) {
		return mkStubKey( name, (ContainerEntity)context.top(), entityType);
	}

	/**
	 * Creates a StubBinding for <code>name</code>. If <code>name</code> is unqualified, considers it is a child of <code>parent</code>.
	 * If  <code>name</code> is (fully)qualified, ignores <code>parent</code>.
	 */
	protected <T extends NamedEntity> IBinding mkStubKey(IASTName name, ContainerEntity parent, java.lang.Class<T> entityType) {
		return mkStubKey(name, parent, entityType);
	}

	public <T extends NamedEntity> IBinding mkStubKey(String name, ContainerEntity parent, java.lang.Class<T> entityType) {
		String simpleName = null;
		QualifiedName qualName = new QualifiedName(name);

		if (qualName.isFullyQualified()) {
			if ( (entityType == Attribute.class) || (entityType == Method.class) ) {
				parent = (ContainerEntity) resolveOrCreate(qualName.nameQualifiers(), /*mayBeNull*/false, /*mustBeClass*/true);
			}
			else {
				parent = (ContainerEntity) resolveOrCreate(qualName.nameQualifiers(), /*mayBeNull*/false, /*mustBeClass*/false);
			}
			simpleName = qualName.unqualifiedName();
		}
		else {
			simpleName = name;
		}
		return StubBinding.getInstance(entityType, CDictionary.mooseName(parent, simpleName));
	}

	/**
	 * Forges a signature for stub BehaviouralEntities
	 * @return "name(&lt;parameters&gt;)" where parametres are substitued by "_" 
	 */
	public String mkStubSig(String name, int nbParam) {
		String sig = name + "(";
		for (int i=0; i < nbParam-1; i++) {
			sig += "_," ;
		}
		if (nbParam > 0) {
			sig += "_)" ;
		}
		else {
			sig += ")" ;
		}
		return sig;
	}

	protected boolean isStubBinding(IBinding bnd) {
		return (bnd instanceof StubBinding);
	}

	protected boolean isMethodBinding(IBinding bnd) {
		if (bnd instanceof ICPPMethod) {
			return true;
		}
		if ( isStubBinding(bnd) && ( ((StubBinding)bnd).getEntityClass().equals(Method.class.getName()) ) ) {
			return true;
		}
		return false;
	}

	public boolean isConstructor(BehaviouralEntity fmx) {
		if ( ! (fmx instanceof Method) ) {
			return false;
		}
		if ( ((Method)fmx).getKind() == null ) {
			return false;
		}
		return ((Method)fmx).getKind().equals(CDictionary.CONSTRUCTOR_KIND_MARKER);
	}

	public boolean isDestructor(BehaviouralEntity fmx) {
		if ( ! (fmx instanceof Method) ) {
			return false;
		}
		if ( ((Method)fmx).getKind() == null ) {
			return false;
		}
		return ((Method)fmx).getKind().equals(CDictionary.DESTRUCTOR_KIND_MARKER);
	}

	public boolean isConstructorBinding(IBinding bnd) {
		if (bnd instanceof ICPPConstructor) {
			return true;
		}
		if (isStubBinding(bnd)) {
			String fullName = ((StubBinding)bnd).getEntityName();
			int i;
			// remove parameters from the name
			i = fullName.indexOf('(');
			if (i > 0) {
				fullName = fullName.substring(0, i);
			}

			String[] parts = fullName.split(QualifiedName.CPP_NAME_SEPARATOR);
			
			i = parts.length;
			if (i < 2) {
				return false; // not a qualified name, can be a simple, top level, function
			}
			return parts[i-2].equals(parts[i-1]);  // className (second to last part) = methName (last part) ?
		}
		return false;
	}

	public boolean isDestructorBinding(IBinding bnd) {
		if ( (bnd instanceof ICPPMethod) && (((ICPPMethod)bnd).isDestructor()) ) {
			return true;
		}
		if (isStubBinding(bnd)) {
			// simplified test. Could look at the name of the class as in isConstructorBinding(bnd)
			return new QualifiedName(((StubBinding)bnd).getEntityName()).unqualifiedName().charAt(0) == '~';
		}
		return false;
	}

	/**
	 * Try to get some binding from a IASTName.
	 * There are two possible way to get bindings: through the Index and by resolveBinding.
	 * but the second may return different bindings for the same entity in different locations
	 * @return a binding or null if none found
	 */
	public IBinding getBinding(IASTName name) {
		IBinding bnd = null;
		if (name == null) {
			return null;
		}
		try {
			bnd = index.findBinding(name);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		return bnd;
	}
	
	public IBinding getFunctionBinding(IASTFunctionDeclarator node, IASTName name) {
		IBinding bnd;
		
		bnd = getBinding(name);   // generic getBinding method

		if (bnd == null) {
			ContainerEntity parent = null;
			String behavName = SignatureBuilderVisitor.signatureFromAST(node);

			// need to find the parent here (although mkStubKey can do it for us)
			// because need to know whether it is a method or a function
			QualifiedName qualName = new QualifiedName(name);
			if (qualName.isFullyQualified()) {
				parent = (ContainerEntity) resolveOrCreate(qualName.nameQualifiers(), /*mayBeNull*/false, /*mustBeClass*/false);
			}
			else {
				parent = context.getTopCppNamespace();
			}

			if (parent instanceof eu.synectique.verveine.core.gen.famix.Class) {
				bnd = mkStubKey( behavName, parent, Method.class);
			}
			else {
				bnd = mkStubKey(behavName, (ContainerEntity) parent, Function.class);
			}
		}
		return bnd;
	}

	/**
	 * Get a behaviouralEntity for the node.
	 * Tries to recover from the binding or the name (does some name resolution based on fully qualified names).
	 * If all fails, will create an entity.
	 */
	public BehaviouralEntity ensureBehavioural(IASTFunctionDeclarator node, IBinding nodeBnd, IASTName nodeName) {
		BehaviouralEntity fmx;

		// just in case this is a definition and we already processed the declaration
		fmx = (BehaviouralEntity) dico.getEntityByKey(nodeBnd);
		// try harder
		if (fmx == null) {
			fmx = ensureBehaviouralFromName(node, nodeBnd, nodeName);
		}
		return fmx;
	}

	public BehaviouralEntity ensureBehaviouralFromName(IASTFunctionDeclarator node, IBinding bnd, IASTName name) {
		String sig;
		ContainerEntity parent;
		BehaviouralEntity fmx;

		// get behavioural name and parent
		if (bnd instanceof StubBinding) {
			String fullname = ((StubBinding)bnd).getEntityName();
			sig = QualifiedName.signatureFromBehaviouralFullname(fullname);

			parent = behaviouralParentFromNameOrContext(fullname);
		}
		else {
			sig = SignatureBuilderVisitor.signatureFromAST(node);

			parent = behaviouralParentFromBindingOrContext(bnd, name);
		}

		// last try to recover behavioural ...
		fmx = (BehaviouralEntity) findInParent(sig, parent, /*recursive*/false);

		// ... create it if failed
		if (fmx == null) {
			if (isMethodBinding(bnd)) {
				fmx = dico.ensureFamixMethod(bnd, new QualifiedName(name).unqualifiedName(), sig, /*owner*/(Type)parent);
			}
			else {                    //   C function or may be a stub ?
				fmx = dico.ensureFamixFunction(bnd, new QualifiedName(name).unqualifiedName(), SignatureBuilderVisitor.signatureFromAST(node), (ContainerEntity)context.top());
			}
		}

		return fmx;
	}

	/**
	 * Search for a Class or Namespace  or Function (in that order) at top level with unqualified name
	 * @return NamedEntity found or null if none match
	 */
	private ContainerEntity findAtTopLevel(String name) {
		for (Type cl : dico.getEntityByName(Type.class, name)) {
			return cl;   // return the 1st type found (if any)
		}
		for (Namespace ns : dico.getEntityByName(Namespace.class, name)) {
			return ns;   // return the 1st namespace found (if any)
		}
		for (Function fct : dico.getEntityByName(Function.class, name)) {
			return fct;   // return the 1st function found (if any)
		}
		return null;
	}

	/**
	 * Search for a unqualified name within the scope of a ContainerEntity.
	 * In the case of looking for a function, name is actually a signature.
	 * @return NamedEntity found or null if none match
	 */
	public NamedEntity findInLocals(String name, ContainerEntity context) {
		for (eu.synectique.verveine.core.gen.famix.Type child : context.getTypes()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		for (Function child : context.getFunctions()) {
			if ( child.getName().equals(name) || child.getSignature().equals(name)) {
				return child;
			}
		}

		return null;
	}

	/**
	 * Search for a unqualified name within the scope of a BehaviouralEntity.
	 * @return NamedEntity found or null if none match
	 */
	public NamedEntity findInLocals(String name, BehaviouralEntity context) {		
		for (StructuralEntity child : context.getLocalVariables()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		for (StructuralEntity child : context.getParameters()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		// if not found call the "super" (by casting the variable)
		return findInLocals(name, (ContainerEntity)context);
	}

	/**
	 * Search for a unqualified name within the scope of a Type.
	 * In the case of looking for a method, name is actually a signature.
	 * @return NamedEntity found or null if none match
	 */
	public NamedEntity findInLocals(String name, Type context) {		

		for (Attribute child : context.getAttributes()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		for (Method child : context.getMethods()) {
			if (child.getSignature().equals(name)) {
				return child;
			}
		}

		// if not found call the "super" (by casting the variable)
		return findInLocals(name, (ContainerEntity)context);
	}

	/**
	 * Search for a unqualified name within the scope of a context.
	 * @return NamedEntity found or null if none match
	 */
	public NamedEntity findInLocals(String name, ScopingEntity context) {		
		for (StructuralEntity child : context.getGlobalVariables()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		for (ScopingEntity child : context.getChildScopes()) {
			if (child.getName().equals(name)) {
				return child;
			}
		}

		// if not found call the "super" (by casting the variable)
		return findInLocals(name, (ContainerEntity)context);
	}

	/**
	 * Search for a unqualified name within the scope of a context.
	 * In the case of looking for a behavioural, name is actually a signature.
	 * If cannot find it and recursive is <code>true</code>, looks in the scope of parent context.
	 * This is a dispatcher method that calls the correct methods from the type of the second parameter
	 * @return NamedEntity found or null if none match
	 */
	public NamedEntity findInParent(String name, NamedEntity context, boolean recursive) {
		NamedEntity found = null;

		if (context == null) {
			return findAtTopLevel(name);
		}
		else if (context instanceof BehaviouralEntity) {
			found = findInLocals(name, (BehaviouralEntity)context);
		}
		else if (context instanceof ScopingEntity) {
			found = findInLocals(name, (ScopingEntity)context);
		}
		else if (context instanceof Type) {
			found = findInLocals(name, (Type)context);
		}
		else {
			// non ContainerEntity, should never happen
			return null;
		}

		if (found != null) {
			return found;
		}

		if (recursive) {
			return findInParent(name, context.getBelongsTo(), recursive);
		}
		else {
			return null;
		}
	}

	/**
	 * Tries to find an entity within the current context, from it's fully qualified name.
	 * If not found, may return null or create a ContainerEntity (Namespace being the default, Class being another possibility)
	 * @param mayBeNull -- if not found, return null or try to create a ContainerEntity
	 * @param mustBeClass -- (ignored if <code>mayBeNull</code> = <code>true</code>) if creating a ContainerEntity, create it as a Class
	 */
	public NamedEntity resolveOrCreate( String name, boolean mayBeNull, boolean mustBeClass) {
		return resolveOrCreate(new QualifiedName(name), mayBeNull, mustBeClass);
	}

	/**
	 * Tries to find an entity within the current context, from it's fully qualified name.
	 * If not found, may return null or create a ContainerEntity (Namespace being the default, Class being another possibility)
	 * @param mayBeNull -- if not found, return null or try to create a ContainerEntity
	 * @param mustBeClass -- (ignored if <code>mayBeNull</code> = <code>true</code>) if creating a ContainerEntity, create it as a Class
	 */
	public NamedEntity resolveOrCreate( QualifiedName name, boolean mayBeNull, boolean mustBeClass) {
		NamedEntity tmp;
		String simpleName;
		ContainerEntity parent = null;
		boolean recursive;

		if (name.isEmpty()) {
			return null;
		}

		simpleName = name.unqualifiedName();

		if (name.isFullyQualified()) {
			// parent is described by nameQualifiers, no recursive search in it
			parent = (ContainerEntity)resolveOrCreate(name.nameQualifiers(), /*mayBeNull*/false, /*mustBeClass*/false);
			recursive = false;
		}
		else {			
			if (name.isAbsoluteQualified()) {
				// parent is root, no recursive search
				parent = null;
				recursive = false;
			}
			else {
				// parent is current stack context, recursive search in it
				parent = (ContainerEntity) context.top();
				recursive = true;
			}
		}

		tmp = findInParent(simpleName, parent, recursive);

		if ( (tmp == null) && (! mayBeNull) ) {
			IBinding bnd;
			
			if (recursive) {
				// if search was recursive and we did not find, create new entity at top level
				// this is the heuristic that gave the best results for now
				// notably, it gives preferences to Namespace creation (over Class) in the following independant of the top context
				parent = null;
			}

			if ( (parent != null) && (! (parent instanceof Namespace)) ) {
				// if parent is not a Namespace (probably a class or method), create a Class because can't have a namespace inside a class or a method
				mustBeClass = true;
			}

			if (mustBeClass) {
				bnd = mkStubKey(simpleName, parent, eu.synectique.verveine.core.gen.famix.Class.class);
				tmp = dico.ensureFamixClass(bnd, simpleName, parent);
			}
			else {
				bnd = mkStubKey(simpleName, parent, Namespace.class);
				tmp = dico.ensureFamixNamespace(bnd, simpleName, (ScopingEntity) parent);
			}
				
		}

		return tmp;
	}

	/**
	 * Assumes binding is not a StubBinding
	 */
	protected ContainerEntity behaviouralParentFromBindingOrContext(IBinding bnd, IASTName name) {
		ContainerEntity parent;

		if (isMethodBinding(bnd)) {
			parent = (ContainerEntity) dico.getEntityByKey( ((ICPPMethod)bnd).getClassOwner() );
			if (parent == null) {
				// happened once in a badly coded case
				if (QualifiedName.isFullyQualified(name)) {
					parent = (ContainerEntity) resolveOrCreate( QualifiedName.parentNameFromEntityFullname(name.toString()), /*mayBeNull*/false, /*mustBeClass*/true );
				}
				else {
					parent = (ContainerEntity) context.top();
				}
			}
		}
		else {
			if (QualifiedName.isFullyQualified(name)) {
				parent = (ContainerEntity) resolveOrCreate( QualifiedName.parentNameFromEntityFullname(name.toString()), /*mayBeNull*/false, /*mustBeClass*/false );
			}
			else {
				parent = (ContainerEntity) context.top();
			}
		}
		return parent;
	}

	protected ContainerEntity behaviouralParentFromNameOrContext(String name) {
		ContainerEntity parent;
		QualifiedName qualName = new QualifiedName(name);
		if (qualName.isFullyQualified()) {
			parent = (ContainerEntity) resolveOrCreate( qualName.nameQualifiers(), /*mayBeNull*/false, /*mustBeClass*/true);
		}
		else {
			parent = (ContainerEntity) context.top();
		}
		return parent;
	}

}
