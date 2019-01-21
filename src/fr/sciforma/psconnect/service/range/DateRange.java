/*
 * © 2012 Sciforma. Tous droits réservés. 
 */
package fr.sciforma.psconnect.service.range;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.service.enumeration.MatinApresMidiJourneeEnum;

/**
 * range of dates without time
 * 
 * XXX : déplacer la logique.
 */
public class DateRange implements Iterable<Date>, Serializable {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"dd/MM/yyyy");

	private static final long serialVersionUID = -6843599654800258742L;

	/**
	 * date de début de la période
	 */
	private Date dateDebut;

	/**
	 * date de fin de la période
	 */
	private Date dateFin;

	/**
	 * information sur la plage de temps de la date du début
	 */
	private MatinApresMidiJourneeEnum periodeDebut = MatinApresMidiJourneeEnum.Journee;

	/**
	 * information sur la plage de temps entre les deux dates
	 */
	private MatinApresMidiJourneeEnum periodeMilieu = MatinApresMidiJourneeEnum.Journee;

	/**
	 * information sur la plage de temps de la date de fin
	 */
	private MatinApresMidiJourneeEnum periodeFin = MatinApresMidiJourneeEnum.Journee;

	public DateRange(Date dateDebut, Date dateFin) throws BusinessException {
		if (dateDebut != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(dateDebut);
			calendar.set(Calendar.MILLISECOND, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.HOUR_OF_DAY, 0);

			this.dateDebut = calendar.getTime();
		}
		if (dateFin != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(dateFin);
			calendar.set(Calendar.MILLISECOND, 999);
			calendar.set(Calendar.SECOND, 59);
			calendar.set(Calendar.MINUTE, 59);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			this.dateFin = calendar.getTime();
		}
		if (this.dateDebut != null && this.dateFin != null
				&& !this.dateDebut.equals(this.dateFin)
				&& this.dateDebut.after(this.dateFin)) {
			throw new BusinessException(
					"La période n'est pas correctement définie car la date de fin <"
							+ dateFin + "> est inférieure à la date de début <"
							+ dateDebut + ">");
		}

	}

	public boolean contains(Date day) {
		return day != null
				&& (isDateDebut(day) || isDateFin(day) || containsStrict(day));
	}

	public boolean containsStrict(Date day) {
		return day != null && (dateFin == null || day.before(dateFin))
				&& (dateDebut == null || day.after(dateDebut));
	}

	public boolean isDateDebut(Date day) {
		Date day2 = null;
		if (day != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(day);
			calendar.set(Calendar.MILLISECOND, 0);
			calendar.set(Calendar.SECOND, 0);
			calendar.set(Calendar.MINUTE, 0);
			calendar.set(Calendar.HOUR_OF_DAY, 0);
			day2 = calendar.getTime();
		}
		return dateDebut != null && day2 != null && dateDebut.equals(day2);
	}

	public boolean isDateFin(Date day) {
		Date day2 = null;
		if (day != null) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(day);
			calendar.set(Calendar.MILLISECOND, 999);
			calendar.set(Calendar.SECOND, 59);
			calendar.set(Calendar.MINUTE, 59);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			day2 = calendar.getTime();
		}
		return dateFin != null && day2 != null && dateFin.equals(day2);
	}

	public boolean isInfini() {
		return dateDebut == null || dateFin == null;
	}

	public Date getAverageDate() {
		return new Date((dateDebut.getTime() + dateFin.getTime()) / 2);
	}

	public Iterator<Date> iterator() {
		if (isInfini())
			throw new IllegalStateException();

		return new Iterator<Date>() {

			private Date dateCurrent;

			public boolean hasNext() {
				return this.dateCurrent == null
						|| contains(calulate().getTime());
			}

			public Date next() {

				if (dateCurrent == null) {
					this.dateCurrent = dateDebut;
				} else if (!contains(dateCurrent)) {
					throw new NoSuchElementException();
				} else {
					this.dateCurrent = calulate().getTime();
				}
				return this.dateCurrent;
			}

			private Calendar calulate() {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(this.dateCurrent);
				calendar.add(Calendar.DATE, 1);
				return calendar;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	public MatinApresMidiJourneeEnum getPeriodeDebut() {
		return periodeDebut;
	}

	public void setPeriodeDebut(MatinApresMidiJourneeEnum periodeDebut) {
		this.periodeDebut = periodeDebut;
	}

	public MatinApresMidiJourneeEnum getPeriodeMilieu() {
		return periodeMilieu;
	}

	public void setPeriodeMilieu(MatinApresMidiJourneeEnum periodeMilieu) {
		this.periodeMilieu = periodeMilieu;
	}

	public MatinApresMidiJourneeEnum getPeriodeFin() {
		return periodeFin;
	}

	public void setPeriodeFin(MatinApresMidiJourneeEnum periodeFin) {
		this.periodeFin = periodeFin;
	}

	/**
	 * @return the dateDebut
	 */
	public Date getDateDebut() {
		return dateDebut;
	}

	/**
	 * @return the dateFin
	 */
	public Date getDateFin() {
		return dateFin;
	}

	@Override
	public String toString() {
		return (this.dateDebut != null ? DATE_FORMAT.format(this.dateDebut)
				: "-inf")
				+ getPeriodeDebut().name().substring(0, 1)
				+ (getPeriodeMilieu() == MatinApresMidiJourneeEnum.Journee ? " - "
						: " - " + getPeriodeMilieu().name().substring(0, 1)
								+ " - ")
				+ (this.dateFin != null ? DATE_FORMAT.format(this.dateFin)
						: "+inf") + getPeriodeFin().name().substring(0, 1);
	}
}
