package org.serest4j.http.idserver.policy;

import java.util.Arrays;

import org.serest4j.http.idserver.HttpKeyValidator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

@SuppressWarnings("serial")
public class TMAccessRolesValidatorTag extends TagSupport {

	public static final String SCOPE_PAGINA = "page";
	public static final String SCOPE_BODY = "body";

	public static final String MODE_ALL = "all";
	public static final String MODE_ANY = "any";

	private String scope = null;
	private Object[] objRoles = null;
	private String parametroRoles = null;
	private boolean modoInverso = false;
	private boolean modoValidacionCompleta = false;

	public void setMode(String mode) {
		if( mode != null  &&  MODE_ALL.equals(mode.trim().toLowerCase()) ) {
			this.modoValidacionCompleta = true;
		}
		else {
			this.modoValidacionCompleta = false;
		}
	}

	public void setScope(String scope) {
		if( scope != null  &&  SCOPE_PAGINA.equals(scope.trim().toLowerCase()) ) {
			this.scope = SCOPE_PAGINA;
		}
		else {
			this.scope = null;	
		}
	}

	public void setRoles(String roles) {
		this.parametroRoles = roles;
		this.modoInverso = false;
		if( this.objRoles != null ) {
			Arrays.fill(this.objRoles, null);
		}
		this.objRoles = null;
		String str = roles == null ? "" : roles.trim();
		while( str.length() > 0  &&  str.charAt(0) == '!' ) {
			str = str.substring(1);
			this.modoInverso = true;
		}
		String[] strRoles = str == null ? new String[0] : str.replace(',', ';').split(";");
		this.objRoles = new String[strRoles.length];
		int i = 0;
		if( strRoles.length > 0 ) {
			String rol = null;
			for( String r : strRoles ) {
				if( r != null ) {
					rol = r.trim();
					if( rol.length() > 0 ) {
						this.objRoles[i] = rol;
						i++;
					}
				}
			}
		}
		this.objRoles = Arrays.copyOf(this.objRoles, i);
	}

	@Override
	public void release() {
		scope = null;
		Object[] _objRoles = objRoles;
		objRoles = null;
		if( _objRoles != null )
			Arrays.fill(_objRoles, null);
		_objRoles = null;
		parametroRoles = null;
		modoInverso = false;
		modoValidacionCompleta = false;
	}

	public int doStartTag() throws JspException {
		boolean cumple = false;
		if( modoValidacionCompleta ) {
			cumple = HttpKeyValidator.cumpleTodosRoles((HttpServletRequest)pageContext.getRequest(), objRoles);
		}
		else {
			cumple = HttpKeyValidator.cumpleAlgunRol((HttpServletRequest)pageContext.getRequest(), objRoles);
		}
		if( modoInverso ) {
			cumple = !cumple;
		}
		if( cumple ) {
			return EVAL_BODY_INCLUDE;
		}
		else if( scope != null ) {
			throw new JspException("No se cumple ninguno de los roles " + parametroRoles);
		}
		return SKIP_BODY;
	}
}
