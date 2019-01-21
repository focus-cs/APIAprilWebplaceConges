/*
 * © 2013 Sciforma. Tous droits réservés. 
 */
package april.model;

import java.io.Serializable;
import java.util.Date;

import fr.sciforma.psconnect.service.enumeration.MatinApresMidiJourneeEnum;

public class RecordCalendar implements Serializable {

	private static final long serialVersionUID = 2253828931777720037L;

	private String cmat;

	private Date dtjre;

	private MatinApresMidiJourneeEnum periode;

	public MatinApresMidiJourneeEnum getPeriode() {
		return periode;
	}

	public void setPeriode(MatinApresMidiJourneeEnum periode) {
		this.periode = periode;
	}

	private StatusEnum status;

	public void setCmat(String cmat) {
		this.cmat = cmat;
	}

	public void setDtjre(Date dtjre) {
		this.dtjre = dtjre;
	}

	public void setStatus(StatusEnum status) {
		this.status = status;
	}

	public StatusEnum getStatus() {
		return this.status;

	}

	public String getCmat() {
		return this.cmat;
	}

	public Date getDtjre() {
		return this.dtjre;
	}

	@Override
	public String toString() {
		return "<cmat:" + cmat + " dtjre:" + dtjre + " :" + periode + " :"
				+ status + ">";
	}

}
