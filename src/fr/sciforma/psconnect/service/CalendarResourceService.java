/*
 * © 2009 Sciforma. Tous droits réservés. 
 */
package fr.sciforma.psconnect.service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import fr.sciforma.psconnect.service.enumeration.MatinApresMidiJourneeEnum;
import fr.sciforma.psconnect.service.range.DateRange;

/**
 * Manager no working period
 * 
 */
public interface CalendarResourceService {

	/**
	 * add on the given range of dates, no working period with a given reason to
	 * the given resource
	 * 
	 * @param resourceID
	 *            ID of the resource
	 * @param dateRange
	 *            range of dates
	 * @param codeRaison
	 *            reason
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	void addNoWorkPeriodOnRange(String resourceID, DateRange dateRange,
			String codeRaison) throws TechnicalException, BusinessException;

	/**
	 * remove no working period to the given resource on the given range of
	 * dates
	 * 
	 * @param resourceID
	 *            ID of the resource
	 * @param dateRange
	 *            range of dates
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	void removeNoWorkPeriodOnRange(String resourceID, DateRange dateRange)
			throws TechnicalException, BusinessException;

	/**
	 * return the list of day in no working period
	 * 
	 * @param resourceID
	 * @param dateRange
	 * @return
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllNonWorkPeriodInRange(
			String resourceID, DateRange dateRange) throws TechnicalException,
			BusinessException;

	/**
	 * return the list of day in no working period in active calendar
	 * 
	 * @param resourceID
	 * @param dateRange
	 * @return
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllActiveNonWorkPeriodInRange(
			String resourceID, DateRange dateRange) throws TechnicalException,
			BusinessException;

	/**
	 * return the list of day in no working period in pending calendar
	 * 
	 * @param resourceID
	 * @param dateRange
	 * @return
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllPendingNonWorkPeriodInRange(
			String resourceID, DateRange dateRange) throws TechnicalException,
			BusinessException;

	/**
	 * add on the date and period, a no working period with a given reason to
	 * the given resource
	 * 
	 * @param resourceID
	 *            ID of the resource
	 * @param date
	 *            date of noWorkPeriod
	 * @param codeRaison
	 *            reason
	 * @param periods
	 *            no work periods
	 * @throws TechnicalException
	 * @throws BusinessException
	 */
	void addNoWorkPeriods(String resourceID, Date date, String codeRaison,
			Collection<int[][]> periods) throws TechnicalException,
			BusinessException;

}
