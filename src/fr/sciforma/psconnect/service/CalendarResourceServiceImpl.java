/*
 * © 2009 Sciforma. Tous droits réservés. 
 */
package fr.sciforma.psconnect.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.time.DateUtils;

import com.sciforma.psnext.api.AccessException;
import com.sciforma.psnext.api.Cal;
import com.sciforma.psnext.api.CalDay;
import com.sciforma.psnext.api.DataFormatException;
import com.sciforma.psnext.api.InvalidDataException;
import com.sciforma.psnext.api.InvalidParameterException;
import com.sciforma.psnext.api.LockException;
import com.sciforma.psnext.api.NonWorkPeriod;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.PermissionException;
import com.sciforma.psnext.api.Project;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;
import com.sciforma.psnext.api.Task;
import com.sciforma.psnext.api.UnlockedSaveException;
import com.sciforma.psnext.api.WorkPeriod;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import fr.sciforma.psconnect.manager.ProjectManager;
import fr.sciforma.psconnect.manager.ResourceManager;
import fr.sciforma.psconnect.service.enumeration.MatinApresMidiJourneeEnum;
import fr.sciforma.psconnect.service.range.DateRange;
import org.pmw.tinylog.Logger;

/**
 * implementation des services de manipulation des calendriers des ressources
 *
 * TODO : javadoc
 *
 * TODO : flag use existing special days.
 *
 * TODO : force period
 *
 * TODO : correct use of timezone
 *
 */
public class CalendarResourceServiceImpl implements CalendarResourceService {

    private static final int[][] DEFAULT_AFTERNOON_PERIOD = {{14, 0},
    {18, 0}};

    private static final int[][] DEFAULT_MORNING_PERIOD = {{8, 0}, {12, 0}};

    private final class HourMinuteComparator implements Comparator<WorkPeriod> {

        public int compare(WorkPeriod o1, WorkPeriod o2) {
            if (o1 == null || o2 == null) {
                return 0;
            }
            return new CompareToBuilder()
                    .append(o1.getStartHour(), o2.getStartHour())
                    .append(o1.getStartMinute(), o2.getFinishMinute())
                    .toComparison();
        }
    }

    private ResourceManager resourceManager;

    private ProjectManager projectManager;

    /**
     * plage du matin par défaut
     */
    private int[][] plageHoraireMatin = DEFAULT_MORNING_PERIOD;

    /**
     * plage du soir par défaut
     */
    private int[][] plageHoraireApresMidi = DEFAULT_AFTERNOON_PERIOD;

    /**
     * Mettre à jour le calendrier des proposées
     */
    private boolean overwritePendingCalendar = false;

    /**
     * Utiliser les jours de semaine;
     */
    private boolean useEffectiveDays = false;

    /**
     * Utiliser les jours fériées;
     */
    private boolean useEffectiveSpecialDays = false;

    /**
     * XXX: BUG ? le code congés est le nom de l'activité
     */
    private boolean hasTranslateNonProjectTask = true;

    private boolean needPopulateNonProjectTask = true;

    private Map<String, String> taskNonProjectMap = new HashMap<String, String>();

    private Map<String, String> reverseTaskNonProjectMap = new HashMap<String, String>();

    public CalendarResourceServiceImpl(ResourceManager resourceManager,
            ProjectManager projectManager) throws BusinessException {
        this.resourceManager = resourceManager;
        this.projectManager = projectManager;
        rePopulateNonProjectTask();
    }

    /**
     * @param plageHoraireMatin the plageHoraireMatin to set
     */
    public final void setPlageHoraireMatin(int heureDebut, int minuteDebut,
            int heureFin, int minuteFin) {
        this.plageHoraireMatin[0][0] = heureDebut;
        this.plageHoraireMatin[0][1] = minuteDebut;
        this.plageHoraireMatin[1][0] = heureFin;
        this.plageHoraireMatin[1][1] = minuteFin;
    }

    /**
     * fluent setter
     *
     * @param plageHoraireMatin the plageHoraireMatin to set
     * @return this
     */
    public final CalendarResourceServiceImpl withPlageHoraireMatin(
            int heureDebut, int minuteDebut, int heureFin, int minuteFin) {
        setPlageHoraireMatin(heureDebut, minuteDebut, heureFin, minuteFin);
        return this;
    }

    /**
     * @param plageHoraireMidi the plageHoraireMidi to set
     */
    public final void setPlageHoraireMidi(int heureDebut, int minuteDebut,
            int heureFin, int minuteFin) {
        this.plageHoraireApresMidi[0][0] = heureDebut;
        this.plageHoraireApresMidi[0][1] = minuteDebut;
        this.plageHoraireApresMidi[1][0] = heureFin;
        this.plageHoraireApresMidi[1][1] = minuteFin;
    }

    /**
     * fluent setter
     *
     * @param plageHoraireMidi the plageHoraireMidi to set
     * @return this
     */
    public final CalendarResourceServiceImpl withPlageHoraireApresMidi(
            int heureDebut, int minuteDebut, int heureFin, int minuteFin) {
        setPlageHoraireMidi(heureDebut, minuteDebut, heureFin, minuteFin);
        return this;
    }

    /**
     * @param useEffectiveDays the useEffectiveDays to set
     */
    public final void setUseEffectiveDays(boolean useEffectiveDays) {
        this.useEffectiveDays = useEffectiveDays;
    }

    /**
     * fluent setter
     *
     * @param useEffectiveDays the useEffectiveDays to set
     * @return this
     */
    public final CalendarResourceServiceImpl withUseEffectiveDays(
            boolean useEffectiveDays) {
        this.useEffectiveDays = useEffectiveDays;
        return this;
    }

    /**
     * @param useEfficiveSpecialDays the useSpecialDays to set
     */
    public final void setUseEfficiveSpecialDays(boolean useEffectiveSpecialDays) {
        this.useEffectiveSpecialDays = useEffectiveSpecialDays;
    }

    /**
     * fluent setter
     *
     * @param useEfficiveSpecialDay the useEfficiveSpecialDays to set
     * @return this
     */
    public final CalendarResourceServiceImpl withUseEfficiveSpecialDays(
            boolean useEffectiveSpecialDays) {
        this.useEffectiveSpecialDays = useEffectiveSpecialDays;
        return this;
    }

    /**
     * @param overwritePendingCalendar the overwritePendingCalendar to set
     */
    public final void setOverwritePendingCalendar(
            boolean overwritePendingCalendar) {
        this.overwritePendingCalendar = overwritePendingCalendar;
    }

    /**
     * fluent setter
     *
     * @param overwritePendingCalendar the overwritePendingCalendar to set
     */
    public final CalendarResourceServiceImpl withOverwritePendingCalendar(
            boolean overwritePendingCalendar) {
        this.overwritePendingCalendar = overwritePendingCalendar;
        return this;
    }

    @SuppressWarnings("unchecked")
    public void addNoWorkPeriodOnRange(String resourceId, DateRange dateRange,
            String codeRaison, boolean pendingOnly, Session session)
            throws BusinessException {
        // recherche la resource
        Resource resource = getResource(resourceId);
        try {
            Logger.info(" ##### pendingCal before lock: "
                    + resource.getPendingCalendar().getName());
        } catch (AccessException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        lock(resource);
        try {
            // calendrier actif
            Cal activeCal = getActiveCal(resource);
            List<CalDay> activeCalDays = getSpecialDays(activeCal);
            if (activeCalDays == null) {
                activeCalDays = new ArrayList<CalDay>();
            }

            // calendrier proposé
            Cal pendingCal = overwritePendingCalendar ? resource
                    .getPendingCalendar() : null;
            Logger.info(" ######## resource name : "
                    + resource.getStringField("Name"));
            Logger.info("########### overwritePendingCalendar : "
                    + overwritePendingCalendar);
            Logger.info(" ##### pendingCal : "
                    + pendingCal.getBaseCalendar().getName());
            Logger.info(" ##### pendingCal specialDays size : "
                    + pendingCal.getSpecialDays().size());
            for (CalDay calDay : (List<CalDay>) pendingCal.getSpecialDays()) {

                // Logger.info("$$$$$$$ special days - " + calDay.getDay());
                if (calDay.getWorkPeriods() != null) {
                    for (WorkPeriod workPeriod : (List<NonWorkPeriod>) calDay
                            .getWorkPeriods()) {
                        if (workPeriod instanceof NonWorkPeriod) {
                            NonWorkPeriod noWorkPeriod = (NonWorkPeriod) workPeriod;
                            // Logger.info(" XXXXXXXXX START : "
                            // + noWorkPeriod.getStartHour());
                            // Logger.info(" XXXXXXXXX FINISH : "
                            // + noWorkPeriod.getFinishHour());
                        }
                    }
                }

            }
            Logger.info(" ##### pendingCal effectiveSpecialDays size : "
                    + pendingCal.getEffectiveSpecialDays().size());

            List<CalDay> effectiveSpecialDays = pendingCal
                    .getEffectiveSpecialDays();

            List<CalDay> specialDays = pendingCal.getSpecialDays();

            List<CalDay> daysToRemove = new ArrayList<CalDay>();
            for (CalDay calDay : specialDays) {

                for (CalDay calDay2 : effectiveSpecialDays) {

                    if (calDay.getDay().equals(calDay2.getDay())) {
                        daysToRemove.add(calDay2);
                    }

                }
                // effectiveSpecialDays.remove(calDay);

            }

            Map<Date, CalDay> halfDayMap = new HashMap<Date, CalDay>();
            for (CalDay calDay : (List<CalDay>) pendingCal
                    .getEffectiveSpecialDays()) {

                // Logger.info("$$$$$$$ EffectiveSpecial days - " +
                // calDay.getDay());
                if (calDay.getWorkPeriods() != null) {
                    for (WorkPeriod workPeriod : (List<NonWorkPeriod>) calDay
                            .getWorkPeriods()) {
                        if (workPeriod instanceof NonWorkPeriod) {
                            NonWorkPeriod noWorkPeriod = (NonWorkPeriod) workPeriod;
                            // Logger.info(" XXXXXXXXX START : "
                            // + noWorkPeriod.getStartHour());
                            // Logger.info(" XXXXXXXXX FINISH : "
                            // + noWorkPeriod.getFinishHour());
                        } else {

                            halfDayMap.put(roundDate(calDay, session).getDay(),
                                    roundDate(calDay, session));

                            WorkPeriod xWorkPeriod = workPeriod;
                            // Logger.info(" XXXXXXXXX START : "
                            // + xWorkPeriod.getStartHour());
                            // Logger.info(" XXXXXXXXX FINISH : "
                            // + xWorkPeriod.getFinishHour());
                        }
                    }
                }

            }

            Logger.info("########### removeAll specialDays : "
                    + effectiveSpecialDays.removeAll(daysToRemove));
            List<Date> dates = new ArrayList<Date>();
            for (CalDay calDay : effectiveSpecialDays) {
                dates.add((roundDate(calDay, session)).getDay());
                Logger.info(" §§§§§§§§§§ halfdays : " + calDay.getDay());
                calDay = roundDate(calDay, session);
                dates.add(calDay.getDay());
            }
            // Logger.info("########### removeAll specialDays : "+effectiveSpecialDays.removeAll(specialDays));

            List<CalDay> halfDays = effectiveSpecialDays;

            List<CalDay> pendingCalDays = overwritePendingCalendar
                    && pendingCal != null ? getSpecialDays(pendingCal) : null;

            for (Date date : dateRange) {
                Logger.info("### DATE : " + date);

                List<WorkPeriod> createWorkPeriods = createWorkPeriods(
                        activeCal, date,
                        codeRaison,
                        // cas daterange = 1 jours --> periodeDebut
                        dateRange.isDateFin(dateRange.getDateDebut()) ? dateRange
                                .getPeriodeDebut()
                                : // cas de la borne de début
                                dateRange.isDateDebut(date)
                                && dateRange.getPeriodeDebut() == MatinApresMidiJourneeEnum.ApresMidi ? dateRange
                                        .getPeriodeDebut()
                                        // cas de la borne de fin
                                        : dateRange.isDateFin(date)
                                        && dateRange.getPeriodeFin() == MatinApresMidiJourneeEnum.Matin ? dateRange
                                                .getPeriodeFin()
                                                : // cas normal
                                                dateRange.getPeriodeMilieu());
                //
                // for (WorkPeriod workPeriod : createWorkPeriods) {
                // Logger.info("### workPeriod start : "
                // + workPeriod.getStartHour());
                // Logger.info("### workPeriod end : "
                // + workPeriod.getFinishHour());
                // }

                Logger.info("### createWorkPeriods.isEmpty() : "
                        + createWorkPeriods.isEmpty());
                if (!createWorkPeriods.isEmpty()) {
                    CalDay calDay = null;
                    for (CalDay tmpCalDay : activeCalDays) {
                        if (DateUtils.truncate(date, Calendar.DATE).equals(
                                DateUtils.truncate(tmpCalDay.getDay(),
                                        Calendar.DATE))) {
                            calDay = tmpCalDay;
                            Logger.debug("réutilisation du calday et merge des work period");
                            try {
                                for (WorkPeriod workPeriod : createWorkPeriods) {
                                    if (workPeriod instanceof NonWorkPeriod) {
                                        calDay.addNonWorkPeriod((NonWorkPeriod) workPeriod);
                                    }
                                }
                            } catch (ConcurrentModificationException e) {
                                Logger.error(e);
                            }
                            break;
                        }
                    }

                    if (calDay == null) {
                        Logger.debug("création du calday");
                        calDay = new CalDay(date, createWorkPeriods);
                    }

                    if (!pendingOnly) {
                        Logger.debug("l'ajout de l'absence dans le calendrier actif");
                        activeCalDays.add(calDay);
                    }

                    Logger.info("############# overwritePendingCalendar && pendingCalDays != null && !pendingCalDays.isEmpty() : "
                            + overwritePendingCalendar
                            + " && "
                            + pendingCalDays != null + " && "
                            + !pendingCalDays.isEmpty());

                    if (overwritePendingCalendar && pendingCalDays != null
                            && !pendingCalDays.isEmpty()) {
                        Logger.debug("l'ajout de l'absence dans le calendrier proposé");

                        pendingCal.getSpecialDays();
                        Logger.info("########### day name : "
                                + roundDate(calDay, session).getDay());

                        // for (WorkPeriod workPeriod : workPeriods) {
                        // Logger.info("***** workPeriod allday : "
                        // + workPeriod.allDay());
                        // Logger.info("***** workPeriod start : "
                        // + workPeriod.getStartHour());
                        // Logger.info("***** workPeriod finish : "
                        // + workPeriod.getFinishHour());
                        // }
                        Logger.info("############# calDay workPeriods : "
                                + calDay.getWorkPeriods().size());
                        /*
                         * Contournement pour ne pa prendre en compte les jour
                         * pas travaillé dans le calendrier par defaut
                         */

                        // for (Date dateKey : halfDayMap.keySet()) {
                        // Logger.info(" xxxxxxx date : " + dateKey);
                        // }
                        Logger.info(" date List : " + dates.size());

                        Logger.info(" ///////////////////  : "
                                + halfDayMap.containsKey(roundDate(calDay,
                                                session).getDay()));
                        if (!dates
                                .contains(roundDate(calDay, session).getDay())
                                || halfDayMap.containsKey(roundDate(calDay,
                                                session).getDay())) {
                            if (halfDayMap.containsKey(roundDate(calDay,
                                    session).getDay())) {
                                setPlageHoraireMatin(8, 0, 12, 0);
                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(
                                        plageHoraireMatin[0][0],
                                        plageHoraireMatin[0][1],
                                        plageHoraireMatin[1][0],
                                        plageHoraireMatin[1][1], "Absence");

                                List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();
                                listWorkPeriods.add(nonWorkPeriod);
                                Logger.info("##### roundDate(calDay).getDay() : "
                                        + roundDate(calDay, session).getDay());
                                CalDay calDayx = new CalDay(calDay.getDay(),
                                        listWorkPeriods);

                                pendingCalDays.add(calDayx);

                            } else {
                                calDay = roundDate(calDay, session);
                                pendingCalDays.add(calDay);

                            }
                            Logger.info("############# GOOOOOOOOOOOOOOOOOOOOOL ");

                        }

                    }
                }
            }

            for (CalDay calDay2 : pendingCalDays) {

                Logger.info("¤¤¤¤¤¤¤¤¤ pendingcalDays : " + calDay2.getDay());

            }
            if (overwritePendingCalendar && pendingCalDays != null
                    && !pendingCalDays.isEmpty()) {
                Logger.debug("Mise à jour du calendrier proposé");
                pendingCal
                        .setReason(String.valueOf(System.currentTimeMillis()));

                pendingCal.setSpecialDays(pendingCalDays);

                resource.setPendingCalendar(pendingCal);

                try {
                    resource.savePendingCalendar();
                } catch (PSException e) {
                    Logger.error(e.getMessage(), e);
                    throw new TechnicalException(e);
                }
            }

            if (!pendingOnly) {
                Logger.debug("Mise à jour du calendrier actif");

                activeCal.setReason(String.valueOf(System.currentTimeMillis()));
                activeCal.setSpecialDays(activeCalDays);

                try {
                    resource.setActiveCalendar(activeCal);
                } catch (PSException e) {
                    Logger.error(e.getMessage(), e);
                    throw new TechnicalException(e);
                }
            }

            save(resource);
        } catch (AccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (DataFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (PSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            unlock(resource);
        }
    }

    public void addNoWorkPeriods(String resourceID, Date date,
            String codeRaison, Collection<int[][]> periods)
            throws BusinessException {
        if (periods == null || periods.size() == 0) {
            throw new BusinessException(
                    "Une periodes horaires doivent être renseignées");
        }
        // recherche la resource
        Resource resource = getResource(resourceID);

        lock(resource);
        try {
            // calendrier actif
            Cal activeCal = getActiveCal(resource);
            List<CalDay> activeCalDays = getSpecialDays(activeCal);
            if (activeCalDays == null) {
                activeCalDays = new ArrayList<CalDay>();
            }

            List<WorkPeriod> list = new LinkedList<WorkPeriod>();

            if (useEffectiveDays) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                CalDay effectiveCalDay = activeCal
                        .getEffectiveDefaultDay(calendar
                                .get(Calendar.DAY_OF_WEEK));
                list = effectiveCalDay.getWorkPeriods();
            }

            // calendrier proposé
            Cal pendingCal = overwritePendingCalendar ? getPendingCal(resource)
                    : null;
            List<CalDay> pendingCalDays = overwritePendingCalendar
                    && pendingCal != null ? getSpecialDays(pendingCal) : null;

            for (int[][] period : periods) {
                list.add(createNoWorkingPeriod(period[0][0], period[0][1],
                        period[1][0], period[1][1], codeRaison));
            }

            activeCalDays.add(new CalDay(date, list));

            if (overwritePendingCalendar && pendingCalDays != null) {
                pendingCalDays.add(new CalDay(date, list));
            }

            activeCal.setReason(String.valueOf(System.currentTimeMillis()));
            activeCal.setSpecialDays(activeCalDays);

            try {
                resource.setActiveCalendar(activeCal);
            } catch (PSException e) {
                Logger.error(e.getMessage(), e);
                throw new TechnicalException(e);
            }

            if (overwritePendingCalendar && pendingCalDays != null
                    && !pendingCalDays.isEmpty()) {
                Logger.debug("Mise à jour du calendrier proposé");
                pendingCal
                        .setReason(String.valueOf(System.currentTimeMillis()));
                pendingCal.setSpecialDays(pendingCalDays);

                try {
                    resource.setPendingCalendar(pendingCal);
                    resource.savePendingCalendar();
                } catch (PSException e) {
                    Logger.error(e.getMessage(), e);
                    throw new TechnicalException(e);
                }
            }

            save(resource);
        } finally {
            unlock(resource);
        }

    }

    private List<WorkPeriod> createWorkPeriods(Cal cal, Date date,
            String codeRaison, MatinApresMidiJourneeEnum apresMidiJourneeEnum)
            throws BusinessException {
        if (!useEffectiveDays && !useEffectiveSpecialDays) {
            return new LinkedList<WorkPeriod>(
                    Arrays.asList(createTwoWorkPeriod(codeRaison,
                                    apresMidiJourneeEnum, this.plageHoraireMatin,
                                    this.plageHoraireApresMidi)));
        }

        List<WorkPeriod> list = null;
        if (useEffectiveSpecialDays && cal.getEffectiveSpecialDays() != null
                && cal.getEffectiveSpecialDays().isEmpty()) {
            for (CalDay specialDays : (List<CalDay>) cal
                    .getEffectiveSpecialDays()) {
                if (date.equals(specialDays.getDay())) {
                    list = (List<WorkPeriod>) specialDays.getWorkPeriods();
                    break;
                }
            }
        }
        if (useEffectiveDays && list == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            CalDay effectiveCalDay = cal.getEffectiveDefaultDay(calendar
                    .get(Calendar.DAY_OF_WEEK));

            if (effectiveCalDay != null) {
                list = (List<WorkPeriod>) effectiveCalDay.getWorkPeriods();
            }
        }

        if (list == null || list.isEmpty() || list.size() == 1) {
            return new LinkedList<WorkPeriod>(
                    Arrays.asList(createTwoWorkPeriod(codeRaison,
                                    apresMidiJourneeEnum, this.plageHoraireMatin,
                                    this.plageHoraireApresMidi)));
        }

        Collections.sort(list, new HourMinuteComparator());

        WorkPeriod matin = list.get(0);
        if (apresMidiJourneeEnum != MatinApresMidiJourneeEnum.ApresMidi) {
            matin = createNoWorkingPeriod(matin.getStartHour(),
                    matin.getStartMinute(), matin.getFinishHour(),
                    matin.getFinishMinute(), codeRaison);
        }
        list.set(0, matin);

        int index = list.size() - 1;
        WorkPeriod apresmidi = list.get(index);
        if (apresMidiJourneeEnum != MatinApresMidiJourneeEnum.Matin) {
            apresmidi = createNoWorkingPeriod(apresmidi.getStartHour(),
                    apresmidi.getStartMinute(), apresmidi.getFinishHour(),
                    apresmidi.getFinishMinute(), codeRaison);
        }
        list.set(index, apresmidi);

        return list;
    }

    private WorkPeriod[] createTwoWorkPeriod(String codeRaison,
            MatinApresMidiJourneeEnum apresMidiJourneeEnum, int[][] matin,
            int[][] apresmidi) throws BusinessException {
        WorkPeriod[] ret = {
            (apresMidiJourneeEnum == MatinApresMidiJourneeEnum.ApresMidi ? createWorkingPeriod(
            matin[0][0], matin[0][1], matin[1][0], matin[1][1])
            : createNoWorkingPeriod(matin[0][0], matin[0][1],
            matin[1][0], matin[1][1], codeRaison)),
            (apresMidiJourneeEnum == MatinApresMidiJourneeEnum.Matin ? createWorkingPeriod(
            apresmidi[0][0], apresmidi[0][1], apresmidi[1][0],
            apresmidi[1][1]) : createNoWorkingPeriod(
            apresmidi[0][0], apresmidi[0][1], apresmidi[1][0],
            apresmidi[1][1], codeRaison))};
        return ret;
    }

    @Override
    public void removeNoWorkPeriodOnRange(String resourceID, DateRange dateRange)
            throws TechnicalException, BusinessException {
        removeNoWorkPeriodOnRange(resourceID, dateRange, null);
    }

    @SuppressWarnings("unchecked")
    public void removeNoWorkPeriodOnRange(String resourceId,
            DateRange dateRange, String codeReasonToClean)
            throws BusinessException {
        Resource resource = getResource(resourceId);

        lock(resource);
        try {
            Cal activeCal = getActiveCal(resource);
            List<CalDay> activeCalDays = getSpecialDays(activeCal);

            if (activeCalDays != null && !activeCalDays.isEmpty()) {
                List<CalDay> activeCalDaysToRemove = new LinkedList<CalDay>();

                // calendrier proposé
                Cal pendingCal = overwritePendingCalendar ? getPendingCal(resource)
                        : null;
                List<CalDay> pendingCalDays = overwritePendingCalendar
                        && pendingCal != null ? getSpecialDays(pendingCal)
                                : null;
                List<CalDay> pendingCalDaysToRemove = overwritePendingCalendar ? new LinkedList<CalDay>()
                        : null;

                for (CalDay calDay : activeCalDays) {
                    Date day = calDay.getDay();
                    if ((dateRange.isDateDebut(day) && dateRange
                            .getPeriodeDebut() == MatinApresMidiJourneeEnum.ApresMidi)
                            || (dateRange.isDateFin(day) && dateRange
                            .getPeriodeFin() == MatinApresMidiJourneeEnum.Matin)) {
                        // date debut et matin ou date de fin et
                        // après-midi
                        MatinApresMidiJourneeEnum demiJournee = dateRange
                                .isDateDebut(day) ? MatinApresMidiJourneeEnum.Matin
                                        : MatinApresMidiJourneeEnum.ApresMidi;

                        Map<MatinApresMidiJourneeEnum, String> extractTimesMap = extractTimesMap(
                                null, calDay);

                        String codeReason = extractTimesMap
                                .get(MatinApresMidiJourneeEnum.Journee);

                        if (codeReason != null) {
                            List<WorkPeriod> createWorkPeriods = createWorkPeriods(
                                    activeCal, day, codeReason, demiJournee);
                            calDay.getWorkPeriods().clear();
                            calDay.getWorkPeriods().addAll(createWorkPeriods);

                            if (overwritePendingCalendar
                                    && pendingCalDays != null
                                    && !pendingCalDays.isEmpty()) {
                                CalDay pendingDay = extractCalDaySameDay(
                                        pendingCalDays, calDay);

                                if (pendingCal != null) {
                                    pendingDay.getWorkPeriods().clear();
                                }
                            }
                        }
                    } else if (dateRange.contains(day)) {
                        activeCalDaysToRemove.add(calDay);

                        if (overwritePendingCalendar && pendingCalDays != null
                                && !pendingCalDays.isEmpty()) {
                            CalDay pendingDay = extractCalDaySameDay(
                                    pendingCalDays, calDay);

                            if (pendingDay != null) {
                                pendingCalDaysToRemove.add(pendingDay);
                            }
                        }
                    }
                }

                // exclure les non code reason to clean.
                Iterator<CalDay> iterator = activeCalDaysToRemove.iterator();
                while (iterator.hasNext()) {
                    CalDay calDay = (CalDay) iterator.next();
                    if (calDay.getWorkPeriods() != null) {
                        for (WorkPeriod workPeriod : (List<WorkPeriod>) calDay
                                .getWorkPeriods()) {
                            if (workPeriod instanceof NonWorkPeriod) {
                                NonWorkPeriod period = (NonWorkPeriod) workPeriod;
                                if (!taskNonProjectMap.get(codeReasonToClean)
                                        .equals(period.getNonWorkTypeID())) {
                                    Logger.debug("keep the cal day <"
                                            + calDay.getDay() + ">");

                                    iterator.remove();
                                    break;
                                }
                            }
                        }
                    }
                }

                activeCalDays.removeAll(activeCalDaysToRemove);
                activeCal.setSpecialDays(activeCalDays);

                try {
                    resource.setActiveCalendar(activeCal);
                } catch (PSException e) {
                    Logger.error(e.getMessage(), e);
                    throw new TechnicalException(e);
                }

                if (overwritePendingCalendar && pendingCal != null
                        && pendingCalDays != null && !pendingCalDays.isEmpty()) {

                    // exclure les non code reason to clean.
                    Iterator<CalDay> iterator2 = pendingCalDaysToRemove
                            .iterator();
                    while (iterator2.hasNext()) {
                        CalDay calDay = (CalDay) iterator2.next();
                        if (calDay.getWorkPeriods() != null) {
                            for (WorkPeriod workPeriod : (List<WorkPeriod>) calDay
                                    .getWorkPeriods()) {
                                if (workPeriod instanceof NonWorkPeriod) {
                                    NonWorkPeriod period = (NonWorkPeriod) workPeriod;
                                    if (!taskNonProjectMap.get(
                                            codeReasonToClean).equals(
                                                    period.getNonWorkTypeID())) {
                                        Logger.debug("keep the cal day <"
                                                + calDay.getDay() + ">");

                                        iterator.remove();
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    pendingCal.setSpecialDays(pendingCalDays);
                    resource.setPendingCalendar(pendingCal);
                }
                save(resource);
            } else {
                Logger.debug("le calendrier actif est non renseigné");
            }
        } finally {
            unlock(resource);
        }
    }

    public Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllNonWorkPeriodInRange(
            String resourceId, DateRange dateRange) throws BusinessException {
        List<CalDay> effectiveSpecialDays = getSpecialDays(getActiveCal(getResource(resourceId)));
        Map<Date, Map<MatinApresMidiJourneeEnum, String>> datesMap = new LinkedHashMap<Date, Map<MatinApresMidiJourneeEnum, String>>();

        if (effectiveSpecialDays != null) {
            for (CalDay calDay : effectiveSpecialDays) {
                Date day = calDay.getDay();
                if (dateRange.contains(day)) {
                    Map<MatinApresMidiJourneeEnum, String> timesMap = extractTimesMap(
                            datesMap.get(day), calDay);
                    datesMap.put(DateUtils.truncate(day, Calendar.DATE),
                            timesMap);
                }
            }
        }

        return datesMap;
    }

    public Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllActiveNonWorkPeriodInRange(
            String resourceId, DateRange dateRange) throws BusinessException {
        List<CalDay> effectiveSpecialDays = getSpecialDays(getActiveCal(getResource(resourceId)));
        Map<Date, Map<MatinApresMidiJourneeEnum, String>> datesMap = new LinkedHashMap<Date, Map<MatinApresMidiJourneeEnum, String>>();

        if (effectiveSpecialDays != null) {
            for (CalDay calDay : effectiveSpecialDays) {
                Date day = calDay.getDay();
                if (dateRange.contains(day)) {
                    Map<MatinApresMidiJourneeEnum, String> timesMap = extractTimesMap(
                            datesMap.get(day), calDay);
                    datesMap.put(DateUtils.truncate(day, Calendar.DATE),
                            timesMap);
                }
            }
        }

        return datesMap;
    }

    public Map<Date, Map<MatinApresMidiJourneeEnum, String>> findAllPendingNonWorkPeriodInRange(
            String resourceId, DateRange dateRange) throws BusinessException {
        List<CalDay> effectiveSpecialDays = getSpecialDays(getPendingCal(getResource(resourceId)));
        Map<Date, Map<MatinApresMidiJourneeEnum, String>> datesMap = new LinkedHashMap<Date, Map<MatinApresMidiJourneeEnum, String>>();

        if (effectiveSpecialDays != null) {
            for (CalDay calDay : effectiveSpecialDays) {
                Date day = calDay.getDay();
                if (dateRange.contains(day)) {
                    Map<MatinApresMidiJourneeEnum, String> timesMap = extractTimesMap(
                            datesMap.get(day), calDay);
                    datesMap.put(DateUtils.truncate(day, Calendar.DATE),
                            timesMap);
                }
            }
        }

        return datesMap;
    }

    @SuppressWarnings("unchecked")
    private Map<MatinApresMidiJourneeEnum, String> extractTimesMap(
            Map<MatinApresMidiJourneeEnum, String> timesMap, CalDay calDay) {
        if (timesMap == null) {
            timesMap = new LinkedHashMap<MatinApresMidiJourneeEnum, String>();
        }
        for (WorkPeriod workPeriod : (List<WorkPeriod>) calDay.getWorkPeriods()) {
            if (workPeriod instanceof NonWorkPeriod) {
                NonWorkPeriod nonWorkPeriod = (NonWorkPeriod) workPeriod;
                String codeReason = reverseTaskNonProjectMap.get(nonWorkPeriod
                        .getNonWorkTypeID());
                if (nonWorkPeriod.getStartHour() < plageHoraireMatin[1][0]) {
                    String codeApresMidi = timesMap
                            .get(MatinApresMidiJourneeEnum.ApresMidi);
                    if (codeApresMidi != null
                            && codeApresMidi.equals(codeReason)) {
                        timesMap.remove(MatinApresMidiJourneeEnum.ApresMidi);
                        timesMap.put(MatinApresMidiJourneeEnum.Journee,
                                codeReason);
                    } else {
                        timesMap.put(MatinApresMidiJourneeEnum.Matin,
                                codeReason);
                    }
                } else {
                    String codeMatin = timesMap
                            .get(MatinApresMidiJourneeEnum.Matin);
                    if (codeMatin != null && codeMatin.equals(codeReason)) {
                        timesMap.remove(MatinApresMidiJourneeEnum.Matin);
                        timesMap.put(MatinApresMidiJourneeEnum.Journee,
                                codeReason);
                    } else {
                        timesMap.put(MatinApresMidiJourneeEnum.ApresMidi,
                                codeReason);
                    }
                }
            }
        }
        return timesMap;
    }

    @SuppressWarnings("unchecked")
    private List<CalDay> getSpecialDays(Cal cal) throws BusinessException {
        if (cal == null) {
            return new LinkedList<CalDay>();
        }
        return cal.getSpecialDays();
    }

    private Resource getResource(String resourceId) throws BusinessException {
        Resource resource = resourceManager.findResourceById(resourceId);
        if (resource == null) {
            throw new BusinessException("La ressource <" + resourceId
                    + "> n'a pas été trouvé.");
        }
        return resource;
    }

    private void lock(Resource resource) throws BusinessException {
        try {
            resource.lock();
        } catch (LockException e) {
            try {
                throw new BusinessException("La ressource <"
                        + resource.getStringField("ID")
                        + "> est verrouillée par <" + e.getLockingUser() + ">");
            } catch (DataFormatException e1) {
                Logger.error(e1.getMessage(), e1);
                throw new TechnicalException(e1, e1.getMessage());
            } catch (PSException e1) {
                Logger.error(e1.getMessage(), e1);
                throw new TechnicalException(e1);
            }
        } catch (PermissionException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e, e.getMessage());
        } catch (AccessException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e, e.getMessage());
        } catch (PSException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e);
        }
    }

    private void save(Resource resource) throws BusinessException {
        try {
            resource.save(false);
        } catch (UnlockedSaveException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e);
        } catch (PSException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e);
        }
    }

    private void unlock(Resource resource) {
        try {
            resource.unlock();
        } catch (PSException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e);
        }
    }

    private NonWorkPeriod createNoWorkingPeriod(int beginHour, int beginMinute,
            int endHour, int endMinute, String codeRaison)
            throws BusinessException {
        String reasonNonProject;
        if (hasTranslateNonProjectTask) {
            reasonNonProject = taskNonProjectMap.get(codeRaison);
            if (reasonNonProject == null) {
                throw new BusinessException("Le code absence <" + codeRaison
                        + "> n'existe pas dans PSNext.");
            }
        } else {
            reasonNonProject = codeRaison;
        }

        try {
            return new NonWorkPeriod(beginHour, beginMinute, endHour,
                    endMinute, reasonNonProject);
        } catch (InvalidParameterException e) {
            Logger.error(e.getMessage(), e);
            throw new BusinessException(
                    "Le code absence <"
                    + codeRaison
                    + "> n'existe pas dans PSNext ou n'est pas utilisable dans les calendriers.");
        } catch (InvalidDataException e) {
            Logger.error(e.getMessage(), e);
            throw new BusinessException(e.getMessage());
        }
    }

    private WorkPeriod createWorkingPeriod(int beginHour, int beginMinute,
            int endHour, int endMinute) throws BusinessException {
        try {
            return new WorkPeriod(beginHour, beginMinute, endHour, endMinute);
        } catch (InvalidDataException e) {
            Logger.error(e.getMessage(), e);
            throw new BusinessException(e.getMessage());
        }
    }

    private Cal getActiveCal(Resource resource) throws BusinessException {
        try {
            return resource.getActiveCalendar();
        } catch (AccessException e) {
            Logger.error(e.getMessage(), e);
            throw new BusinessException(
                    "L'accès au calendrier active de la ressource n'est pas possible : "
                    + e.getCause());
        } catch (PSException e) {
            Logger.error(e.getMessage(), e);
            throw new TechnicalException(e);
        }
    }

    private Cal getPendingCal(Resource resource) throws BusinessException {
        try {
            return resource.getPendingCalendar();
        } catch (AccessException e) {
            Logger.warn(
                    "L'accès au calendrier proposé de la ressource n'est pas possible : <"
                    + e.getMessage() + ">", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void rePopulateNonProjectTask() throws BusinessException {
        if (needPopulateNonProjectTask) {
            Logger.debug("Constitution de la liste des activités hors projet "
                    + taskNonProjectMap);

            Project lHP = projectManager.openNonProject();

            try {
                for (Task element : (List<Task>) lHP.getTaskOutlineList()) {
                    try {
                        taskNonProjectMap.put(element.getStringField("ID"),
                                element.getStringField("name"));

                        reverseTaskNonProjectMap.put(
                                element.getStringField("name"),
                                element.getStringField("ID"));
                    } catch (DataFormatException e) {
                        Logger.error(e.getMessage(), e);
                        throw new TechnicalException(e);
                    } catch (PSException e) {
                        Logger.error(e.getMessage(), e);
                        throw new TechnicalException(e);
                    }
                }
            } catch (PSException e) {
                Logger.error(e.getMessage(), e);
                throw new TechnicalException(e);
            }

            try {
                lHP.close();
            } catch (AccessException e) {
                Logger.error(e.getMessage(), e);
                throw new TechnicalException(e);
            } catch (PSException e) {
                Logger.error(e.getMessage(), e);
                throw new TechnicalException(e);
            }

            Logger.debug("Liste des activités hors projet "
                    + taskNonProjectMap);

            needPopulateNonProjectTask = false;
        }
    }

    private CalDay extractCalDaySameDay(List<CalDay> pendingCalDays,
            CalDay calDayToMatch) {
        if (pendingCalDays != null) {
            for (CalDay calDay : pendingCalDays) {
                if (new EqualsBuilder().append(calDay.getDay(),
                        calDayToMatch.getDay()).isEquals()) {
                    return calDay;
                }
            }
        }
        return null;
    }

    @Override
    public void addNoWorkPeriodOnRange(String resourceID, DateRange dateRange,
            String codeRaison) throws TechnicalException, BusinessException {
        // TODO Auto-generated method stub

    }

    public CalDay roundDate(CalDay calDay, Session session) {

        Date date = calDay.getDay();
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTimeZone(session.getServerTimeZone());
        } catch (PSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Logger.error(" ERROR ");
        }
        calendar.setTime(date);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR, 8);
        calDay.setDay(calendar.getTime());
        return calDay;
    }

}
