package eu.synectique.verveine.extractor.visitors;

import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.index.IIndexBinding;

import ch.akuhn.fame.Repository;
import eu.synectique.verveine.core.Dictionary;
import eu.synectique.verveine.core.gen.famix.Attribute;
import eu.synectique.verveine.core.gen.famix.Class;
import eu.synectique.verveine.core.gen.famix.ContainerEntity;
import eu.synectique.verveine.core.gen.famix.Function;
import eu.synectique.verveine.core.gen.famix.IndexedFileAnchor;
import eu.synectique.verveine.core.gen.famix.Method;
import eu.synectique.verveine.core.gen.famix.NamedEntity;
import eu.synectique.verveine.core.gen.famix.Namespace;
import eu.synectique.verveine.core.gen.famix.Package;
import eu.synectique.verveine.core.gen.famix.Parameter;
import eu.synectique.verveine.core.gen.famix.ParameterizableClass;
import eu.synectique.verveine.core.gen.famix.ScopingEntity;
import eu.synectique.verveine.core.gen.famix.SourceAnchor;
import eu.synectique.verveine.core.gen.famix.SourcedEntity;
import eu.synectique.verveine.core.gen.famix.Type;
import eu.synectique.verveine.core.gen.famix.TypeAlias;
import eu.synectique.verveine.core.gen.famix.UnknownVariable;
import eu.synectique.verveine.extractor.utils.StubBinding;

public class CDictionary extends Dictionary<IIndexBinding> {

	/**
	 * Separator in fully qualified package name
	 */
	public static final String PACKAGE_NAME_SEPARATOR = "::";

	public final static String DESTRUCTOR_KIND_MARKER = "destructor";

 	public CDictionary(Repository famixRepo) {
		super(famixRepo);
	}

	protected NamedEntity getEntityIfNotNull(IIndexBinding key) {
		if (key == null) {
			return null;
		}
		else {
			return keyToEntity.get(key);
		}
	}
	
	/**
	 * Adds location information to a Famix Entity.
	 * Location informations are: <b>name</b> of the source file and <b>position</b> in this file.
	 * @param fmx -- Famix Entity to add the anchor to
	 * @param filename -- name of the file being visited
	 * @param ast -- ASTNode, where the information are extracted
	 * @return the Famix SourceAnchor added to fmx. May be null in case of incorrect/null parameter
	 */
	public SourceAnchor addSourceAnchor(SourcedEntity fmx, String filename, IASTFileLocation anchor) {
		IndexedFileAnchor fa = null;

		if ( (fmx == null) || (anchor == null) ) {
			return null;
		}

		// position in source file
		int beg = anchor.getNodeOffset();
		int end = beg + anchor.getNodeLength();

		// create the Famix SourceAnchor
		fa = new IndexedFileAnchor();
		fa.setStartPos(beg);
		fa.setEndPos(end);
		fa.setFileName(filename);

		fmx.setSourceAnchor(fa);
		famixRepo.add(fa);

		return fa;
	}

	public Namespace ensureFamixNamespace(IIndexBinding key, String name, ScopingEntity parent) {
		Namespace fmx = super.ensureFamixNamespace(key, name);
		if (parent != null) {
			fmx.setParentScope(parent);
		}
		return fmx;
	}

	public Package ensureFamixPackage(String name, Package parent) {
		String fullname = mooseName(parent, name);
		IIndexBinding key = StubBinding.getInstance(Package.class, fullname);
		Package fmx = super.ensureFamixEntity(Package.class, key, name, /*persitIt*/true);
		fmx.setIsStub(false);
		if (parent != null) {
			fmx.setParentPackage(parent);
		}
		return fmx;
	}

	public TypeAlias ensureFamixTypeAlias(IIndexBinding key, String name, ContainerEntity owner) {
		TypeAlias fmx;

		fmx = super.ensureFamixEntity(TypeAlias.class, key, name, /*persistIt*/true);
		fmx.setContainer(owner);

		return fmx;
	}

	public eu.synectique.verveine.core.gen.famix.Class ensureFamixClass(IIndexBinding key, String name, ContainerEntity owner) {
		eu.synectique.verveine.core.gen.famix.Class fmx;
		fmx = (Class) getEntityIfNotNull(key);
		if (fmx == null) {
			fmx = super.ensureFamixClass(key, name, owner, /*persistIt*/true);
		}
		
		return fmx;
	}

	public ParameterizableClass ensureFamixParameterizableClass(IIndexBinding key, String name, ContainerEntity owner) {
		ParameterizableClass fmx;
		fmx = (ParameterizableClass) getEntityIfNotNull(key);
		if (fmx == null) {
			fmx = super.ensureFamixParameterizableClass(key, name, owner, /*persistIt*/true);
		}
		
		return fmx;
	}

	public Function ensureFamixFunction(IIndexBinding key, String name, String sig, ContainerEntity parent) {
		Function fmx;
		fmx = (Function) getEntityIfNotNull(key);
		if (fmx == null) {
			fmx = super.ensureFamixFunction(key, name, sig, /*returnType*/null, parent, /*persistIt*/true);
			fmx.setCyclomaticComplexity(1);
			fmx.setNumberOfStatements(0);
		}
		return fmx;
	}

	public Method ensureFamixMethod(IIndexBinding key, String name, String sig, Type parent) {
		Method fmx;
		fmx = (Method) getEntityIfNotNull(key);
		if (fmx == null) {
			fmx = super.ensureFamixMethod(key, name, sig, /*returnType*/null, parent, /*persistIt*/true);
			fmx.setCyclomaticComplexity(1);
			fmx.setNumberOfStatements(0);
		}

		return fmx;
	}

	public Attribute ensureFamixAttribute(IIndexBinding key, String name, Type parent) {
		Attribute fmx;
		fmx = (Attribute) getEntityIfNotNull(key);
		if (fmx == null) {
			fmx = super.ensureFamixAttribute(key, name, /*type*/null, parent, /*persistIt*/true);
		}

		return fmx;
	}

	/**
	 * Returns a Famix Parameter associated with the IIndexBinding.
	 * The Entity is created if it does not exist.<br>
	 * Params: see {@link Dictionary#ensureFamixParameter(Object, String, Type, eu.synectique.verveine.core.gen.famix.BehaviouralEntity, boolean)}.
	 * @param persistIt -- whether to persist or not the entity eventually created
	 * @return the Famix Entity found or created. May return null if "bnd" is null or in case of a Famix error
	 */
	public Parameter ensureFamixParameter(IIndexBinding bnd, String name, Method owner) {
		Parameter fmx = null;

		// --------------- to avoid useless computations if we can
		fmx = (Parameter)getEntityByKey(bnd);
		if (fmx != null) {
			return fmx;
		}

		if (fmx == null) {
			fmx = super.createFamixParameter(bnd, name, /*type*/null, owner, /*persistIt*/true);
		}

		return fmx;
	}

	/**
	 * Create an UnknownVariable. parent currently not used
	 */
	public UnknownVariable createFamixUnknownVariable(String name, NamedEntity parent) {
		UnknownVariable fmx;
		
		fmx = ensureFamixEntity(UnknownVariable.class, /*key*/null, name, /*persistIt*/true);
		fmx.setIsStub(true);
		
		return fmx;
	}

	// UTILITIES =========================================================================================================================================
	

	/**
	 * Computes moose name for a ScopingEntity
	 * This is a convenient method to call {@link #mooseName(Namespace)} or {@link #mooseName(Package)}
	 * and to make Java type checker happy
	 */
	protected static String mooseName(ScopingEntity ent, String name) {
		if (ent instanceof Package) {
			return mooseName((Package)ent, name);
		}
		if (ent instanceof Namespace) {
			return mooseName((Namespace)ent, name);
		}
		return name;
	}

	/**
	 * Computes moose name for a Namespace. NOT USED CURRENTLY (but this may change)
	 * MooseName is the concatenation of the moosename of the parent Namescape with the simple name of the Namescape
	 */
	protected static String mooseName(Namespace parent, String name) {
		if (parent != null) {
			return concatMooseName( mooseName(parent.getParentScope(), parent.getName()) , name);
		}
		else {
			return name;
		}
	}
	
	/**
	 * Computes moose name for a Package
	 * MooseName is the concatenation of the moosename of the parent Package with the simple name of the Package
	 */
	protected static String mooseName(Package parent, String name) {
		if (parent != null) {
			return concatMooseName( mooseName(parent.getParentPackage(), parent.getName()) , name);
		}
		else {
			return name;
		}
	}

	protected static String concatMooseName(String prefix, String name) {
		return prefix + PACKAGE_NAME_SEPARATOR + name;
	}

	
}