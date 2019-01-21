/*
 * © 2012 Sciforma. Tous droits réservés. 
 */
package fr.sciforma.psconnect.service.range;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;

/**
 * Week range tools
 * 
 * @author pyard@sciforma.fr
 */
public class WeekRange implements Iterable<DateRange>, Serializable {

	private static final long serialVersionUID = -13515130L;

	private DateRange dateRange;

	public WeekRange(DateRange dateRange) throws BusinessException {
		this.dateRange = dateRange;
	}

	public WeekRange(Date start, Date finish) throws BusinessException {
		this.dateRange = new DateRange(start, finish);
	}

	public boolean contains(Date day) {
		return day != null && dateRange.contains(day);
	}

	public boolean containsStrict(Date day) {
		return day != null && dateRange.containsStrict(day);
	}

	@Override
	public Iterator<DateRange> iterator() {
		if (dateRange.isInfini()) {
			throw new IllegalStateException();
		}

		return new Iterator<DateRange>() {

			private DateRange dateRangeCurrent;

			@Override
			public boolean hasNext() {
				return this.dateRangeCurrent == null
						|| contains(calulate().getDateDebut());
			}

			@Override
			public DateRange next() {

				if (dateRangeCurrent == null) {
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(dateRange.getDateDebut());							
					calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
					try {
						this.dateRangeCurrent = new DateRange(
								dateRange.getDateDebut(), calendar.getTime());
					} catch (BusinessException e) {
						// nop
						throw new TechnicalException(e,
								"début et fin calculés non en accord");
					}
				} else if (!contains(dateRangeCurrent.getDateDebut())) {
					throw new NoSuchElementException();
				} else {
					this.dateRangeCurrent = calulate();
					if (!contains(dateRangeCurrent.getDateFin())) {
						try {
							this.dateRangeCurrent = new DateRange(
									dateRangeCurrent.getDateDebut(),
									dateRange.getDateFin());
						} catch (BusinessException e) {
							// nop
							throw new TechnicalException(e,
									"début et fin calculés non en accord");
						}
					}

				}
				return this.dateRangeCurrent;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			private DateRange calulate() {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(dateRangeCurrent.getDateDebut());

				calendar.add(Calendar.WEEK_OF_YEAR, 1);
				calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				Date dateFin = calendar.getTime();

				calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
				Date dateNewFin = calendar.getTime();

				try {
					return new DateRange(dateFin, dateNewFin);
				} catch (BusinessException e) {
					// nop
					throw new TechnicalException(e, "début <" + dateFin
							+ ">et fin <" + dateNewFin
							+ "> calculées non en accord");
				}
			}
		};
	}

	public DateRange getDateRange() {
		return this.dateRange;
	}

	public List<DateRange> getDateRanges() {
		List<DateRange> list = new LinkedList<DateRange>();
		for (DateRange dateRange : this) {
			list.add(dateRange);
		}
		return list;
	}

	public boolean isDateDebut(Date today) {
		return this.dateRange.isDateDebut(today);
	}

	public boolean isDateFin(Date today) {
		return this.dateRange.isDateFin(today);
	}

	@Override
	public String toString() {
		return this.dateRange.toString();
	}

}
