package getmethod.handlers;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.inject.Named;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.swt.widgets.Shell;
/** <b>Warning</b> : 
  As explained in <a href="http://wiki.eclipse.org/Eclipse4/RCP/FAQ#Why_aren.27t_my_handler_fields_being_re-injected.3F">this wiki page</a>, it is not recommended to define @Inject fields in a handler. <br/><br/>
  <b>Inject the values in the @Execute methods</b>
*/
public class HelloWorldHandler {
	
	@Execute
	public void execute(@Named(IServiceConstants.ACTIVE_SHELL) Shell s) throws JavaModelException, CoreException, FileNotFoundException {
		PrintStream ps = new PrintStream("../t.txt");
		System.setOut(ps);
		ArrayList<unit> units = new ArrayList<unit>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		System.out.println("root" + root.getLocation().toOSString());
		
		IProject[] projects = root.getProjects();
		int i=1;
		// process each project
		for (IProject project : projects) {
	 
			System.out.println("project name: " + project.getName());
	 
			if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
				IJavaProject javaProject = JavaCore.create(project);
				IPackageFragment[] packages = javaProject.getPackageFragments();
	 
				// process each package
				for (IPackageFragment aPackage : packages) {
	 
					// We will only look at the package from the source folder
					// K_BINARY would include also included JARS, e.g. rt.jar
					// only process the JAR files
					if (aPackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
	 
						for (ICompilationUnit unit : aPackage
								.getCompilationUnits()) {
	 
							System.out.println("--class name: "
									+ unit.getElementName());
	 
							IType[] allTypes = unit.getAllTypes();
							for (IType type : allTypes) {
	 
								IMethod[] methods = type.getMethods();
	 
								for (IMethod method : methods) {
									unit tmp=new unit();
									tmp.setClassName(unit.getElementName());
									tmp.setmethod(method.getElementName());
									tmp.setReturnType(method.getReturnType());
									System.out.println(i+":");
									System.out.println("Class:"+tmp.ClassName);
									System.out.println("method:"+tmp.method);
									System.out.println("Return:"+tmp.ReturnType);
									i++;
								}
							}
						}
					}
				}
	 
			}
		
	 
		}
		ps.close();

	}
	
	public class unit{
		String method;
		String ReturnType;
		String ClassName;
		
		private void setmethod(String str) {
			this.method=str;
		}
		
		private void setReturnType(String str) {
			this.ReturnType=str;
		}
		
		private void setClassName(String str) {
			this.ClassName=str;
		}
	}


}
