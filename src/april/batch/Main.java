/*
 * © 2013 Sciforma. Tous droits réservés. 
 */
package april.batch;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.time.DateUtils;

import april.model.RecordCalendar;
import april.model.StatusEnum;

import com.sciforma.psnext.api.AccessException;
import com.sciforma.psnext.api.Cal;
import com.sciforma.psnext.api.CalDay;
import com.sciforma.psnext.api.DatedData;
import com.sciforma.psnext.api.DoubleDatedData;
import com.sciforma.psnext.api.InternalFailure;
import com.sciforma.psnext.api.InvalidDataException;
import com.sciforma.psnext.api.LockException;
import com.sciforma.psnext.api.NonWorkPeriod;
import com.sciforma.psnext.api.PSException;
import com.sciforma.psnext.api.PermissionException;
import com.sciforma.psnext.api.Resource;
import com.sciforma.psnext.api.Session;
import com.sciforma.psnext.api.Timesheet;
import com.sciforma.psnext.api.TimesheetAssignment;
import com.sciforma.psnext.api.UnlockedSaveException;
import com.sciforma.psnext.api.WorkPeriod;
import fr.sciforma.beans.Connector;
import fr.sciforma.beans.SciformaField;

import fr.sciforma.psconnect.exception.BusinessException;
import fr.sciforma.psconnect.exception.TechnicalException;
import fr.sciforma.psconnect.input.CSVFileInputImpl;
import fr.sciforma.psconnect.input.LineFileInput;
import fr.sciforma.psconnect.service.enumeration.MatinApresMidiJourneeEnum;
import fr.sciforma.psconnect.service.range.DateRange;
import fr.sciforma.psconnect.service.range.WeekRange;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.pmw.tinylog.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * Batch : webplace-conges
 *
 * importation des données de webplace
 *
 * @see se réferer à la spécification : SP_APRIL_(remontée des consommés de
 * webplace)-01_02
 */
public class Main {

    // private static final Logger log = Logger.getLogger(Main.class);
	/*
     * Information sur le nom du programme
     */
    private static final String PROGRAM = "webplace-conges";

    /*
     * Version du programme
     */
    private static final String NUMBER = "2.1";

    /*
     * Tag du programme sur subversion
     */
    private static final String TAG_NUMBER = "$Id: Main.java 4129 2014-09-10 15:14:33Z scoronado $";

    private static Date TODAY = new Date();

    private static Map<Integer, Integer> mapDefaultCalDayWorkPeriods;

    private static List<Integer> emptyDays;

    private final static DateFormat DATE_FORMAT = new SimpleDateFormat(
            "yyyyMMdd");

    private final static DecimalFormat DECIMAL_FORMAT = new DecimalFormat(
            "###0.##", new DecimalFormatSymbols(Locale.FRANCE));

    private static NonWorkPeriod tempValue;

    private static DateRange dateRange;

    static int futur, passe, delta;

    private static Date dateDebut, dateFin;

    static double jminimun, jmaximun, jfacminimun, jfacmaximun, sminimun, smaximun,
            sfacminimun, sfacmaximun;

    private static LineFileInput<String[]> lineFileInput;

    private static Map<String, Resource> resourceMap;

    private static Cal temp;

    public static Session mSession;

    //public static ApplicationContext ctx;
    private static String IP;
    private static String PORT;
    private static String CONTEXTE;
    private static String USER;
    private static String PWD;

    private static Properties properties;

    public static void main(String[] args) {

        Logger.info("[main][" + PROGRAM + "][V" + NUMBER + "] Demarrage de l'API: " + new Date());
        try {
            initialisation();
            chargementConfiguration();
            connexion();
            chargementRessource();
            process();
            mSession.logout();
            Logger.info("[main][" + PROGRAM + "][V" + NUMBER + "] Fin de l'API: " + new Date());
        } catch (PSException ex) {
            Logger.error(ex);
        }
        System.exit(0);
    }

    private static void initialisation() {
        properties = new Properties();
        FileInputStream in;

        try {
            in = new FileInputStream(System.getProperty("user.dir") + System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "psconnect.properties");
            properties.load(in);
            in.close();
        } catch (FileNotFoundException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        } catch (IOException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        } catch (NullPointerException ex) {
            Logger.error("Erreur dans la lecture du fichier properties. ", ex);
            System.exit(-1);
        }
        //ctx = new FileSystemXmlApplicationContext(System.getProperty("user.dir") + System.getProperty("file.separator") + "conf" + System.getProperty("file.separator") + "applicationContext.xml");
    }

    private static void chargementConfiguration() throws PSException {
        try {
            Logger.info("Demarrage du chargement des parametres de l'application:" + new Date());
            try {
                futur = Integer.valueOf(properties.getProperty("nbMoisFutur"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <nbMoisFutur> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <nbMoisFutur> n'est pas correctement renseignée.");
            }

            try {
                passe = Integer.valueOf(properties.getProperty("nbMoisPasse"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <nbMoisPasse> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <nbMoisPasse> n'est pas correctement renseignée.");
            }

            // OE.002 : Définition de la période de référence
            Date debutDateRange = new Date();
            debutDateRange = DateUtils.addMonths(TODAY, passe);

            Date finDateRange = new Date();
            finDateRange = DateUtils.addMonths(TODAY, futur);

            Calendar debut = Calendar.getInstance();
            debut.setTime(debutDateRange);
            debut.set(Calendar.DAY_OF_MONTH, 1);
            debut.set(Calendar.MONTH, debut.get(Calendar.MONTH));
            debut.set(Calendar.YEAR, debut.get(Calendar.YEAR));
            debut.set(Calendar.HOUR_OF_DAY, 00);
            debut.set(Calendar.MINUTE, 00);
            debut.set(Calendar.SECOND, 00);
            debut.set(Calendar.MILLISECOND, 00);

            Calendar fin = Calendar.getInstance();
            fin.setTime(finDateRange);
            fin.set(Calendar.DAY_OF_MONTH,
                    fin.getActualMaximum(Calendar.DAY_OF_MONTH));
            fin.set(Calendar.MONTH, fin.get(Calendar.MONTH));
            fin.set(Calendar.YEAR, fin.get(Calendar.YEAR));
            fin.set(Calendar.HOUR_OF_DAY, 23);
            fin.set(Calendar.MINUTE, 59);
            fin.set(Calendar.SECOND, 59);
            fin.set(Calendar.MILLISECOND, 999);

            debutDateRange = debut.getTime();
            finDateRange = fin.getTime();

            dateDebut = debutDateRange;
            dateFin = finDateRange;

            Logger.info(" ======== > debutPeriode : " + debutDateRange);
            Logger.info(" ======== > finPeriode : " + finDateRange);

            dateRange = new DateRange(debutDateRange, finDateRange);

            try {
                jminimun = Double.valueOf(properties.getProperty("journee.minimum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <journee.minimum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <journee.minimum> n'est pas correctement renseignée.");
            }

            try {
                jmaximun = Double.valueOf(properties.getProperty("journee.maximum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <journee.maximum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <journee.maximum n'est pas correctement renseignée.");
            }

            try {
                jfacminimun = Double.valueOf(properties.getProperty("journeefac.minimum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <journee(fac).minimum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <journee(fac).minimum> n'est pas correctement renseignée.");
            }

            try {
                jfacmaximun = Double.valueOf(properties.getProperty("journeefac.maximum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <journee(fac).maximum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <journee(fac).maximum> n'est pas correctement renseignée.");
            }
            try {
                sminimun = Double.valueOf(properties.getProperty("semaine.minimum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <semaine.minimum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <semaine.minimum> n'est pas correctement renseignée.");
            }
            try {
                smaximun = Double.valueOf(properties.getProperty("semaine.maximum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <semaine.maximum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <semaine.maximum> n'est pas correctement renseignée.");
            }
            try {
                sfacminimun = Double.valueOf(properties.getProperty("semainefac.minimum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <semaine(fac).minimum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <semaine(fac).minimum> n'est pas correctement renseignée.");
            }
            try {
                sfacmaximun = Double.valueOf(properties.getProperty("semainefac.maximum"));
            } catch (NumberFormatException exception) {
                Logger.error(
                        "ER.002, la valeur <semaine(fac).maximum> n'est pas correctement renseignée.",
                        exception);

                throw new BusinessException(
                        "ER.002, la valeur <semaine(fac).maximum> n'est pas correctement renseignée.");
            }
            lineFileInput = new CSVFileInputImpl(properties.getProperty("import.file"));

            Logger.info("Fin du chargement des parametres de l'application:" + new Date());
        } catch (Exception ex) {
            Logger.error("Erreur dans la lecture l'intitialisation du parametrage " + new Date(), ex);
            Logger.error(ex);
            System.exit(1);
        }
    }

    private static void connexion() {
        try {
            USER = properties.getProperty("sciforma.user");
            PWD = properties.getProperty("sciforma.pwd");
            IP = properties.getProperty("sciforma.ip");
            CONTEXTE = properties.getProperty("sciforma.ctx");

            Logger.info("Initialisation de la Session:" + new Date());
            String url = IP + "/" + CONTEXTE;
            Logger.info("URL: " + url);
            mSession = new Session(url);
            mSession.login(USER, PWD.toCharArray());
            Logger.info("Connecté: " + new Date() + " à l'instance " + CONTEXTE);
        } catch (PSException ex) {
            Logger.error("Erreur dans la connection de ... " + CONTEXTE, ex);
            Logger.error(ex);
            System.exit(-1);
        } catch (NullPointerException ex) {
            Logger.error("Erreur dans la connection de ... " + CONTEXTE, ex);
            Logger.error(ex);
            System.exit(-1);
        }
    }

    private static void chargementRessource() {
        List<Resource> resourceList;
        resourceMap = new HashMap<String, Resource>();
        try {
            resourceList = mSession.getResourceList();
            for (Resource resource2 : resourceList) {
                resourceMap.put(resource2.getStringField("ID"), resource2);
            }

        } catch (PSException e2) {
            Logger.error(e2);
        }
    }

    protected static void process() throws TechnicalException {
        try {
            tempValue = new NonWorkPeriod(8, 0, 12, 0, "Absence");
        } catch (InvalidDataException ex) {
            Logger.error(ex);
        }
        Map<String, List<RecordCalendar>> map = new HashMap<String, List<RecordCalendar>>();

        // lecture du fichier et préparation de données.
        for (String[] line : lineFileInput.readAll()) {

            if (!(line.length >= 4)) {
                Logger.warn("la ligne <"
                        + Arrays.deepToString(line)
                        + "> n'a pas suffisament d'élements, attendu <4>, seulement <"
                        + line.length + ">");
                continue;
            }

            RecordCalendar recordCalendar = new RecordCalendar();
            recordCalendar.setCmat(line[0]);

            String dtjreAsString = line[1];
            try {
                recordCalendar.setDtjre(DATE_FORMAT.parse(dtjreAsString));
            } catch (ParseException e) {
                Logger.warn("Date <"
                        + "> mal formatée, l'absence n'est pas traitée pour la ligne <"
                        + Arrays.deepToString(line) + ">");
                continue;
            }

            String periodeAsString = line[2];
            if (!"A".equals(periodeAsString) && !"J".equals(periodeAsString)
                    && !"M".equals(periodeAsString)) {
                Logger.warn("WARN - le code période <"
                        + periodeAsString
                        + "> n'est pas correct, valeurs attendues : J, M ou A, l'absence n'est pas traitée pour la ligne <"
                        + Arrays.deepToString(line) + ">");
                continue;
            }

            MatinApresMidiJourneeEnum period = periodeAsString.equals("A") ? MatinApresMidiJourneeEnum.ApresMidi
                    : periodeAsString.equals("J") ? MatinApresMidiJourneeEnum.Journee
                            : MatinApresMidiJourneeEnum.Matin;
            recordCalendar.setPeriode(period);

            recordCalendar.setStatus(line[3].equals("A") ? StatusEnum.ACCEPTED
                    : StatusEnum.WAITING);

            if (map.containsKey(recordCalendar.getCmat())) {
                List<RecordCalendar> cmatLines = map.get(recordCalendar
                        .getCmat());

                boolean fusion = false;

                for (RecordCalendar recordCalendar2 : cmatLines) {
                    /*
                     * Consolidation des lignes même date, même statut pour la
                     * même resource
                     */
                    if (recordCalendar2.getDtjre().equals(
                            recordCalendar.getDtjre())
                            && recordCalendar2.getStatus().equals(
                                    recordCalendar.getStatus())) {
                        recordCalendar2
                                .setPeriode(MatinApresMidiJourneeEnum.Journee);
                        fusion = true;
                    }
                }
                // Logger.info(" **** FUSION " + fusion);
                if (!fusion) {
                    map.get(recordCalendar.getCmat()).add(recordCalendar);
                }

            } else {

                // Logger.info(" **** Nouvelle RESSOURCE");
                List<RecordCalendar> recordList = new ArrayList<RecordCalendar>();
                recordList.add(recordCalendar);
                map.put(recordCalendar.getCmat(), recordList);
            }

        }

        /*
         * test pending calendar retrieval
         */
        int count = 0, total = map.keySet().size();
        // traitement des ressources
        for (String cmat : map.keySet()) {
            count++;

            for (RecordCalendar rcal : map.get(cmat)) {
                Logger.debug("Date de la ligne traitée <" + rcal.getDtjre() + ">");

            }
            Resource resource = null;
            try {

                if (resourceMap.get(cmat) != null) {
                    resource = resourceMap.get(cmat);
                } else {
                    Logger.warn("La ressource d'identifiant <" + cmat
                            + "> n'existe pas");
                    continue;
                }

                String timeEntrySet = resource.getStringField("Time Entry Set");
                String resCalendar = resource.getActiveCalendar()
                        .getBaseCalendar().getName();
                List<CalDay> nonWorkingDaysBaseCal = resource
                        .getActiveCalendar().getBaseCalendar().getSpecialDays();
                List<Date> listDates = new ArrayList<Date>();

                if (nonWorkingDaysBaseCal != null) {
                    for (CalDay calDay : nonWorkingDaysBaseCal) {
                        listDates.add(DATE_FORMAT.parse(DATE_FORMAT
                                .format(calDay.getDay())));
                    }
                }
                // relation between time entry set and control (hard coded)
                boolean jour = false, semaine = false, fac = false;
                if ("Contrôle des CRA".equalsIgnoreCase(timeEntrySet)) {
                    jour = true;
                    semaine = true;
                    fac = false;
                } else if ("Contrôle des CRA (fac)"
                        .equalsIgnoreCase(timeEntrySet)) {
                    jour = true;
                    semaine = true;
                    fac = true;
                } else if ("Contrôle hebdo seul".equalsIgnoreCase(timeEntrySet)) {
                    jour = false;
                    semaine = true;
                    fac = false;
                } else if ("Contrôle hebdo seul (fac)"
                        .equalsIgnoreCase(timeEntrySet)) {
                    jour = false;
                    semaine = true;
                    fac = true;
                }

                Logger.debug("Début du traitement de la ressource <" + cmat + ", "
                        + resource.getStringField("Name") + "> - phase 1 - <"
                        + count + "/" + total + ">");
                Logger.debug("Le calendrier de la ressource est <" + resCalendar
                        + ">");

                // Contrôles des heures avant suppression
                WeekRange weekRange = new WeekRange(dateRange);
                Calendar cal = Calendar.getInstance();
                Calendar weekStart = Calendar.getInstance();
                /*
                 * Modif 20140910
                 */

                for (DateRange week : weekRange) {
                    if (TODAY.before(week.getDateFin())) {
                        continue;
                    }
                    Logger.debug("pour la semaine <" + week + ">");
                    Timesheet timesheet = mSession.getTimesheet(resource,
                            week.getAverageDate());
                    double sumAWeek = 0;

                    boolean weekStartsOnWeekend = false;

                    for (Date date : week) {
                        cal.setTime(date);
                        /*
                         * Exclusion des WE, demande 201404 et mise en place de
                         * filtres pour palier à la mauvaise configuration des
                         * calendriers
                         */
                        weekStart.setTime(week.getDateDebut());

                        if (weekStart.get(Calendar.DAY_OF_WEEK) == 7
                                || weekStart.get(Calendar.DAY_OF_WEEK) == 1) {
                            weekStartsOnWeekend = true;
                            continue;
                        }

                        /*
                         * Modif 20140910 comptabilisation des jours non ouvrés
                         * du calendrier base pour décompte dans les contrôles
                         * par semaine
                         */
                        if (TODAY.before(date)
                                || cal.get(Calendar.DAY_OF_WEEK) == 1
                                || cal.get(Calendar.DAY_OF_WEEK) == 7
                                || ("Absent le lundi".equals(resCalendar) && cal
                                .get(Calendar.DAY_OF_WEEK) == 2)
                                || ("Absent le vendredi".equals(resCalendar) && cal
                                .get(Calendar.DAY_OF_WEEK) == 6)
                                || ("Absent le mardi et le vendredi"
                                .equals(resCalendar) && (cal
                                .get(Calendar.DAY_OF_WEEK) == 3 || cal
                                .get(Calendar.DAY_OF_WEEK) == 6))
                                || listDates.contains(DATE_FORMAT
                                        .parse(DATE_FORMAT.format(date)))) {
                            continue;
                        }
                        Logger.debug("calcul pour le jour <" + date + ">");
                        DoubleDatedData doubleDatedData;

                        double sumADays = 0;

                        for (TimesheetAssignment timesheetAssignment : (List<TimesheetAssignment>) timesheet
                                .getTimesheetAssignmentList()) {
                            List<DoubleDatedData> list = (List<DoubleDatedData>) timesheetAssignment
                                    .getDatedData("Actual Effort",
                                            DatedData.DAY, week.getDateDebut(),
                                            week.getDateFin());

                            for (DoubleDatedData doubleDatedData2 : list) {
                                if (DATE_FORMAT.format(
                                        doubleDatedData2.getStart()).equals(
                                                DATE_FORMAT.format(date))) {
                                    doubleDatedData = doubleDatedData2;

                                    sumADays += doubleDatedData.getData();

                                    break;
                                }
                            }
                        }

                        sumAWeek += sumADays;

                        Logger.debug("nombre d'heure pour le jour <"
                                + sumADays + ">");
                        Logger.debug("nombre d'heure pour la semaine <"
                                + sumAWeek + ">");
                        if (jour) {
                            if (fac) {
                                if (sumADays < jfacminimun) {

                                    Logger.warn("WA.007 - La règle de saisie <"
                                            + timeEntrySet
                                            + "> n'est pas respectée pour la ressource <"
                                            + cmat + " , " + resource
                                            + "> pour le jour <" + date
                                            + ">, il y a <" + sumADays
                                            + "> le minimum est <"
                                            + jfacminimun + "> heures");
                                }
                                if (sumADays > jfacmaximun) {
                                    Logger.warn("WA.007 - La règle de saisie <"
                                            + timeEntrySet
                                            + "> n'est pas respectée pour la ressource <"
                                            + cmat + " , " + resource
                                            + "> pour le jour <" + date
                                            + ">, il y a <" + sumADays
                                            + "> le maximum est <"
                                            + jfacmaximun + "> heures");
                                }
                            } else {
                                if (sumADays < jminimun) {
                                    Logger.warn("WA.007 - La règle de saisie <"
                                            + timeEntrySet
                                            + "> n'est pas respectée pour la ressource <"
                                            + cmat + " , " + resource
                                            + "> pour le jour <" + date
                                            + ">, il y a <" + sumADays
                                            + "> le minimum est <" + jminimun
                                            + "> heures");
                                }
                                if (sumADays > jmaximun) {
                                    Logger.warn("WA.007 - La règle de saisie <"
                                            + timeEntrySet
                                            + "> n'est pas respectée pour la ressource <"
                                            + cmat + " , " + resource
                                            + "> pour le jour <" + date
                                            + ">, il y a <" + sumADays
                                            + "> le maximum est <" + jmaximun
                                            + "> heures");
                                }
                            }
                        }
                    }

                    if (semaine && !weekStartsOnWeekend) {
                        if (fac) {
                            if (sumAWeek < sfacminimun * 8.0) {
                                Logger.warn("WA.007 - La règle de saisie <"
                                        + timeEntrySet
                                        + "> n'est pas respectée pour la ressource <"
                                        + cmat + " , " + resource
                                        + "> pour la semaine <" + week
                                        + ">, il y a <" + sumAWeek / 8.0
                                        + "> le minimum est <" + sfacminimun
                                        + "> jours");
                            }
                            if (sumAWeek > sfacmaximun * 8.0) {
                                Logger.warn("WA.007 - La règle de saisie <"
                                        + timeEntrySet
                                        + "> n'est pas respectée pour la ressource <"
                                        + cmat + " , " + resource
                                        + "> pour la semaine <" + week
                                        + ">, il y a <" + sumAWeek / 8.0
                                        + "> le maximum est <" + sfacmaximun
                                        + "> jours");
                            }
                        } else {
                            if (sumAWeek < sminimun * 8.0) {
                                Logger.warn("WA.007 - La règle de saisie <"
                                        + timeEntrySet
                                        + "> n'est pas respectée pour la ressource <"
                                        + cmat + " , " + resource
                                        + "> pour la semaine <" + week
                                        + ">, il y a <" + sumAWeek / 8.0
                                        + "> le minimum est <" + sminimun
                                        + "> jours");
                            }
                            if (sumAWeek > smaximun * 8.0) {
                                Logger.warn("WA.007 - La règle de saisie <"
                                        + timeEntrySet
                                        + "> n'est pas respectée pour la ressource <"
                                        + cmat + " , " + resource
                                        + "> pour la semaine <" + week
                                        + ">, il y a <" + sumAWeek / 8.0
                                        + "> le maximum est <" + smaximun
                                        + "> jours");
                            }
                        }
                    }

                }

                // OE.003 : Suppression des absences avant l'insertion des
                // données d'importation
                Cal pendingCal = resource.getPendingCalendar();

                Cal activeCal = resource.getActiveCalendar();
                /*
                 * Le calendrier pending prend la valeur du calendrier approuvé
                 * pour être traité en premier
                 */
                temp = pendingCal;
                pendingCal = activeCal;

                List<CalDay> pendingCalDays = pendingCal
                        .getEffectiveSpecialDays();

                List<CalDay> pendingCalDaysToRemove = new ArrayList<CalDay>();
                List<CalDay> pendingCalDaysToAdd = new ArrayList<CalDay>();

                /*
                 * Traitement des absences du calendrier approuvé
                 */
                removeAbsence(resource, pendingCal, pendingCalDays,
                        pendingCalDaysToRemove, pendingCalDaysToAdd, true);
                /*
                 * remise en place des valeurs initiales pour le calendrier
                 * pending
                 */

                pendingCalDays = temp.getEffectiveSpecialDays();
                pendingCal = temp;
                /*
                 * Traitement des absences du calendrier proposé
                 */
                removeAbsence(resource, pendingCal, pendingCalDays,
                        pendingCalDaysToRemove, pendingCalDaysToAdd, false);

                Logger.info("fin de la phase 1 pour la resource <" + cmat
                        + "> - <" + count + "/" + total + ">");
            } catch (PSException e) {
                Logger.error("ERR - Impossible de traiter la ressource <" + cmat
                        + ">", e);

                continue;
            } catch (BusinessException e) {
                Logger.error("phase 1 non réalisée entiérement pour la resource <"
                        + cmat + "> - <" + count + "/" + total + ">", e);

            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            Logger.info("début de la phase 2 pour la resource <" + cmat + "> - <"
                    + count + "/" + total + ">");

            List<RecordCalendar> cmatList = map.get(cmat);

            /*
             * Split en list d'enregistrements en cours et validés
             */
            List<RecordCalendar> recordsApproved = new ArrayList<RecordCalendar>();
            List<RecordCalendar> recordsWaiting = new ArrayList<RecordCalendar>();

            for (RecordCalendar recordCalendar : cmatList) {
                if (recordCalendar.getStatus().equals(StatusEnum.ACCEPTED)) {
                    recordsApproved.add(recordCalendar);
                } else {
                    recordsWaiting.add(recordCalendar);
                }
            }

            // OE.004 : traitement de l'importation phase 2
            Logger.debug("Mise à jour du Calendrier");
            addAbsence(resource, recordsApproved, true);

            // mapping of active calendar
            Map<Date, CalDay> uniqueWorkPeriodActiveCalendarMap = new HashMap<Date, CalDay>();

            Map<Date, CalDay> multipleWorkPeriodsActiveCalendarMap = new HashMap<Date, CalDay>();
            try {
                for (CalDay calDay : (List<CalDay>) resource
                        .getActiveCalendar().getEffectiveSpecialDays()) {

                    if (calDay.getWorkPeriods() != null) {
                        // test de contrôle du nombre de période
                        if (calDay.getWorkPeriods().size() == 1) {
                            uniqueWorkPeriodActiveCalendarMap.put(
                                    roundDate(calDay).getDay(), calDay);
                        } else {
                            multipleWorkPeriodsActiveCalendarMap.put(
                                    roundDate(calDay).getDay(), calDay);
                        }
                    }

                }

            } catch (AccessException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (PSException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            try {

                resource.lock();
                resource.getPendingCalendar().setStatus(Cal.STATUS_REWORK);
                resource.savePendingCalendar();

                // Restore Formation days to the pending
                // calendar
                List<CalDay> listFormationToRestoreOnPendingCal = new ArrayList<CalDay>();
                for (CalDay calDay : (List<CalDay>) temp
                        .getEffectiveSpecialDays()) {

                    if (calDay.getWorkPeriods() != null) {
                        for (WorkPeriod nwp : (List<WorkPeriod>) calDay
                                .getWorkPeriods()) {
                            if (nwp instanceof NonWorkPeriod) {
                                NonWorkPeriod nwp1 = (NonWorkPeriod) nwp;
                            }
                        }
                    }
                }
                List<Date> processedCalDaysPendingCal = new ArrayList<Date>();
                for (CalDay calDay : (List<CalDay>) temp
                        .getEffectiveSpecialDays()) {

                    if (calDay.getWorkPeriods() != null) {

                        int nwCompt = 0;
                        int passCompt = 0;
                        List<WorkPeriod> lwp = new ArrayList<WorkPeriod>();
                        lwp = calDay.getWorkPeriods();
                        for (WorkPeriod wp : (List<WorkPeriod>) calDay
                                .getWorkPeriods()) {
                            passCompt++;

                            if (wp instanceof NonWorkPeriod) {

                                if (!"Absence".equals(((NonWorkPeriod) wp)
                                        .getNonWorkTypeID())) {

                                    tempValue = (NonWorkPeriod) wp;

                                    nwCompt++;
                                }
                            } /*
                             * Mettre la valeur d'origine
                             */ else {

                            }

                            // Formation days to keep, only in Pending calendar
                            if (!multipleWorkPeriodsActiveCalendarMap
                                    .containsKey((roundDate(calDay)).getDay())
                                    && nwCompt != 0) {
                                listFormationToRestoreOnPendingCal.add(calDay);
                            } else if (!uniqueWorkPeriodActiveCalendarMap
                                    .containsKey((roundDate(calDay)).getDay())
                                    && nwCompt != 0) {
                                listFormationToRestoreOnPendingCal.add(calDay);
                            }

                            // Full day or half day formation add
                            if (passCompt == 2 || lwp.size() == 1) {

                                // if (nwCompt == 1) {
                                if (uniqueWorkPeriodActiveCalendarMap
                                        .containsKey(roundDate(calDay).getDay())) {

                                    for (WorkPeriod wp1 : (List<WorkPeriod>) (uniqueWorkPeriodActiveCalendarMap
                                            .get(roundDate(calDay).getDay()))
                                            .getWorkPeriods()) {
                                        if (wp1 instanceof NonWorkPeriod) {
                                            if (!((NonWorkPeriod) wp1)
                                                    .getNonWorkTypeID()
                                                    .contains("Formation")) {
                                                Calendar cal = Calendar
                                                        .getInstance();

                                                if (wp1.getStartHour() > 8) {

                                                    List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();

                                                    NonWorkPeriod nonWorkPeriod2 = new NonWorkPeriod(
                                                            13, 0, 17, 0,
                                                            "Absence");
                                                    listWorkPeriods
                                                            .add(nonWorkPeriod2);

                                                    CalDay calDayx = new CalDay(
                                                            calDay.getDay(),
                                                            listWorkPeriods);

                                                    listFormationToRestoreOnPendingCal
                                                            .add(calDayx);
                                                }// Half day formation
                                                // afternoon
                                                else {
                                                    List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();

                                                    NonWorkPeriod nonWorkPeriod2 = new NonWorkPeriod(
                                                            8, 0, 12, 0,
                                                            "Absence");

                                                    listWorkPeriods
                                                            .add(nonWorkPeriod2);

                                                    CalDay calDayx = new CalDay(
                                                            calDay.getDay(),
                                                            listWorkPeriods);
                                                    // Not a Saturday or
                                                    // Sunday
                                                    Calendar calendar = Calendar
                                                            .getInstance();
                                                    calendar.setTime(calDay
                                                            .getDay());

                                                    if (calendar
                                                            .get(Calendar.DAY_OF_WEEK) != 7
                                                            && calendar
                                                            .get(Calendar.DAY_OF_WEEK) != 1) {
                                                        listFormationToRestoreOnPendingCal
                                                                .add(calDayx);

                                                        if (!processedCalDaysPendingCal
                                                                .contains(calDayx
                                                                        .getDay())) {
                                                            processedCalDaysPendingCal
                                                                    .add(calDayx
                                                                            .getDay());
                                                            if ((calDay
                                                                    .getDay())
                                                                    .after(dateDebut)
                                                                    && (calDay
                                                                    .getDay())
                                                                    .before(dateFin)) {
                                                                Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur l'après midi du <"
                                                                        + calDay.getDay()
                                                                        + ">");
                                                            }
                                                        }
                                                    } else {
                                                        Logger.warn("Tentative de modification du WeekEnd"
                                                                + calDayx
                                                                .getDay());
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }

                                // } else {
                                if (multipleWorkPeriodsActiveCalendarMap
                                        .containsKey(roundDate(calDay).getDay())) {

                                    List<WorkPeriod> wpActiveCalDay = multipleWorkPeriodsActiveCalendarMap
                                            .get(roundDate(calDay).getDay())
                                            .getWorkPeriods();

                                    Calendar calendar = Calendar.getInstance();
                                    calendar.setTime(calDay.getDay());

                                    if (nwCompt == 2) {
                                        if (calendar.get(Calendar.DAY_OF_WEEK) != 7
                                                && calendar
                                                .get(Calendar.DAY_OF_WEEK) != 1) {

                                            listFormationToRestoreOnPendingCal
                                                    .add(calDay);

                                            if (!processedCalDaysPendingCal
                                                    .contains(calDay.getDay())) {
                                                processedCalDaysPendingCal
                                                        .add(calDay.getDay());

                                            }
                                        } else {
                                            Logger.warn("Tentative de modification du WeekEnd : "
                                                    + calDay.getDay());
                                        }
                                    } else if (nwCompt == 1) {

                                        List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();
                                        // Afternoon formation

                                        if (tempValue.getStartHour() > 8) {

                                            if (wpActiveCalDay.get(0) instanceof NonWorkPeriod) {

                                                NonWorkPeriod nonWorkPeriod2 = new NonWorkPeriod(
                                                        8,
                                                        0,
                                                        12,
                                                        0,
                                                        ((NonWorkPeriod) wpActiveCalDay
                                                        .get(0))
                                                        .getNonWorkTypeID());
                                                listWorkPeriods
                                                        .add(nonWorkPeriod2);

                                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(
                                                        13,
                                                        0,
                                                        17,
                                                        0,
                                                        tempValue
                                                        .getNonWorkTypeID());

                                                listWorkPeriods
                                                        .add(nonWorkPeriod);
                                                CalDay calday = new CalDay(
                                                        calDay.getDay(),
                                                        listWorkPeriods);

                                                if (calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 7
                                                        && calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 1) {

                                                    listFormationToRestoreOnPendingCal
                                                            .add(calday);

                                                    if (!processedCalDaysPendingCal
                                                            .contains(calday
                                                                    .getDay())) {
                                                        processedCalDaysPendingCal
                                                                .add(calday
                                                                        .getDay());
                                                        if ((calDay.getDay())
                                                                .after(dateDebut)
                                                                && (calDay
                                                                .getDay())
                                                                .before(dateFin)) {
                                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur l'après midi du <"
                                                                    + calDay.getDay()
                                                                    + ">");
                                                        }
                                                    }

                                                } else {
                                                    Logger.warn("Tentative de modification du WeekEnd");
                                                }
                                            } // TST
                                            else {

                                                WorkPeriod WorkPeriod2 = new WorkPeriod(
                                                        8, 0, 12, 0);
                                                listWorkPeriods
                                                        .add(WorkPeriod2);
                                                // }

                                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(
                                                        13,
                                                        0,
                                                        17,
                                                        0,
                                                        tempValue
                                                        .getNonWorkTypeID());

                                                listWorkPeriods
                                                        .add(nonWorkPeriod);

                                                CalDay calday = new CalDay(
                                                        calDay.getDay(),
                                                        listWorkPeriods);

                                                if (calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 7
                                                        && calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 1) {
                                                    listFormationToRestoreOnPendingCal
                                                            .add(calday);

                                                    if (!processedCalDaysPendingCal
                                                            .contains(calday
                                                                    .getDay())) {
                                                        processedCalDaysPendingCal
                                                                .add(calday
                                                                        .getDay());
                                                        if ((calDay.getDay())
                                                                .after(dateDebut)
                                                                && (calDay
                                                                .getDay())
                                                                .before(dateFin)) {
                                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur l'après midi du <"
                                                                    + calDay.getDay()
                                                                    + ">");
                                                        }
                                                    }
                                                } else {
                                                    Logger.warn("Tentative de modification du WeekEnd"
                                                            + calday.getDay());
                                                }
                                            }
                                        } // Morning
                                        else {

                                            if (wpActiveCalDay.get(1) instanceof NonWorkPeriod) {

                                                NonWorkPeriod nonWorkPeriod2 = new NonWorkPeriod(
                                                        8,
                                                        0,
                                                        12,
                                                        0,
                                                        tempValue
                                                        .getNonWorkTypeID());
                                                listWorkPeriods
                                                        .add(nonWorkPeriod2);

                                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(
                                                        13,
                                                        0,
                                                        17,
                                                        0,
                                                        ((NonWorkPeriod) wpActiveCalDay
                                                        .get(1))
                                                        .getNonWorkTypeID());

                                                listWorkPeriods
                                                        .add(nonWorkPeriod);
                                                CalDay calday = new CalDay(
                                                        calDay.getDay(),
                                                        listWorkPeriods);

                                                if (calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 7
                                                        && calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 1) {
                                                    listFormationToRestoreOnPendingCal
                                                            .add(calday);

                                                    if (!processedCalDaysPendingCal
                                                            .contains(calday
                                                                    .getDay())) {
                                                        processedCalDaysPendingCal
                                                                .add(calday
                                                                        .getDay());
                                                        if ((calDay.getDay())
                                                                .after(dateDebut)
                                                                && (calDay
                                                                .getDay())
                                                                .before(dateFin)) {
                                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur le matin du <"
                                                                    + calDay.getDay()
                                                                    + ">");
                                                        }
                                                    }
                                                } else {
                                                    Logger.warn("Tentative de modification du WeekEnd");
                                                }
                                            } else {

                                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(
                                                        8,
                                                        0,
                                                        12,
                                                        0,
                                                        tempValue
                                                        .getNonWorkTypeID());
                                                listWorkPeriods
                                                        .add(nonWorkPeriod);

                                                NonWorkPeriod nonWorkPeriod2;
                                                // Si pm calDay est
                                                // nonWorkperiod
                                                if (wpActiveCalDay.get(1) instanceof NonWorkPeriod) {
                                                    nonWorkPeriod2 = new NonWorkPeriod(
                                                            13,
                                                            0,
                                                            17,
                                                            0,
                                                            ((NonWorkPeriod) wpActiveCalDay
                                                            .get(1))
                                                            .getNonWorkTypeID());
                                                    listWorkPeriods
                                                            .add(nonWorkPeriod2);
                                                } else {
                                                    WorkPeriod WorkPeriod2 = new WorkPeriod(
                                                            13, 0, 17, 0);
                                                    listWorkPeriods
                                                            .add(WorkPeriod2);
                                                }

                                                CalDay calday = new CalDay(
                                                        calDay.getDay(),
                                                        listWorkPeriods);

                                                if (calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 7
                                                        && calendar
                                                        .get(Calendar.DAY_OF_WEEK) != 1) {
                                                    listFormationToRestoreOnPendingCal
                                                            .add(calday);

                                                    if (!processedCalDaysPendingCal
                                                            .contains(calday
                                                                    .getDay())) {
                                                        processedCalDaysPendingCal
                                                                .add(calday
                                                                        .getDay());
                                                        if ((calDay.getDay())
                                                                .after(dateDebut)
                                                                && (calDay
                                                                .getDay())
                                                                .before(dateFin)) {
                                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur le matin du <"
                                                                    + calDay.getDay()
                                                                    + ">");
                                                        }
                                                    }
                                                } else {
                                                    Logger.warn("Tentative de modification du WeekEnd");
                                                }
                                            }
                                        }

                                    }

                                }

                                // }
                            }

                        }
                    }
                }

                Cal pendingToRestoreWithFormation = resource
                        .getPendingCalendar();

                List<CalDay> specialpendingToRestoreWithFormation = pendingToRestoreWithFormation
                        .getEffectiveSpecialDays();
                specialpendingToRestoreWithFormation
                        .addAll(listFormationToRestoreOnPendingCal);
                pendingToRestoreWithFormation
                        .setSpecialDays(specialpendingToRestoreWithFormation);
                // System.out.println("******SET PENDING CAL*****");
                resource.setPendingCalendar(pendingToRestoreWithFormation);
                resource.savePendingCalendar();
                // System.out.println("******SAVE*****");
                resource.save(false);
            } catch (AccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (UnlockedSaveException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (PSException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    resource.unlock();
                } catch (InternalFailure e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (AccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            addAbsence(resource, recordsWaiting, false);

            Logger.info("phase 2 entiérement pour la resource <" + cmat + "> - <"
                    + count + "/" + total + ">");
        }

    }

    /**
     * @param resource
     * @param recordsByStatus
     */
    @SuppressWarnings({"unchecked", "unchecked"})
    protected static void addAbsence(Resource resource,
            List<RecordCalendar> recordsByStatus, boolean statusApproved) {

        /*
         * Cotrole pour savoir si le jour à traité contient un congé autre que
         * NPP0001
         */
        List<CalDay> ListEffectiveCalDays = null;
        List<CalDay> ListSpecialCalDays = null;
        List<Date> daysToKeepUnchanged = new ArrayList<Date>();

        try {
            ListSpecialCalDays = resource.getPendingCalendar()
                    .getBaseCalendar().getSpecialDays();
            mapDefaultCalDayWorkPeriods = new HashMap<Integer, Integer>();
            /*
             * Modif 20140131, map des jours par defaut du calendrier base
             */

            List<WorkPeriod> defaultCalDayMonday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.MONDAY) != null) {
                defaultCalDayMonday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.MONDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDayTuesday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.TUESDAY) != null) {
                defaultCalDayTuesday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.TUESDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDayWednesday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.WEDNESDAY) != null) {
                defaultCalDayWednesday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.WEDNESDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDayThursday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.THURSDAY) != null) {
                defaultCalDayThursday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.THURSDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDayFriday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.FRIDAY) != null) {
                defaultCalDayFriday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.FRIDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDaySaturday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.SATURDAY) != null) {
                defaultCalDaySaturday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.SATURDAY)
                        .getWorkPeriods();
            }
            List<WorkPeriod> defaultCalDaySunday = new ArrayList<WorkPeriod>();
            if (resource.getPendingCalendar().getBaseCalendar()
                    .getDefaultDay(Calendar.SUNDAY) != null) {
                defaultCalDaySunday = resource.getPendingCalendar()
                        .getBaseCalendar().getDefaultDay(Calendar.SUNDAY)
                        .getWorkPeriods();
            }
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.MONDAY,
                    defaultCalDayMonday == null ? 0 : defaultCalDayMonday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.TUESDAY,
                    defaultCalDayTuesday == null ? 0 : defaultCalDayTuesday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.WEDNESDAY,
                    defaultCalDayWednesday == null ? 0 : defaultCalDayWednesday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.THURSDAY,
                    defaultCalDayThursday == null ? 0 : defaultCalDayThursday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.FRIDAY,
                    defaultCalDayFriday == null ? 0 : defaultCalDayFriday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.SATURDAY,
                    defaultCalDaySaturday == null ? 0 : defaultCalDaySaturday
                            .size());
            mapDefaultCalDayWorkPeriods.put(
                    Calendar.SUNDAY,
                    defaultCalDaySunday == null ? 0 : defaultCalDaySunday
                            .size());
            emptyDays = new ArrayList<Integer>();
            for (int day : mapDefaultCalDayWorkPeriods.keySet()) {
                if (mapDefaultCalDayWorkPeriods.get(day) == 0) {

                    emptyDays.add(day);

                }
            }

            for (CalDay calDay : ListSpecialCalDays) {

                if (calDay.getWorkPeriods() == null) {
                    try {
                        daysToKeepUnchanged.add(DATE_FORMAT.parse(DATE_FORMAT
                                .format(calDay.getDay())));
                    } catch (ParseException e) {
                        // TODO Auto-generated catch block
                        Logger.error("ERR - Parse exception");
                        e.printStackTrace();

                    }
                }
            }
        } catch (AccessException e2) {
            // TODO Auto-generated catch block
            Logger.error("Erreur à la récuperation des jours à conserver", e2);

        }
        /*
         * Affichage pour controler les dates a conserver en provenance des
         * calendriers base de la ressource
         */
        // for (Date date : daysToKeepUnchanged) {
        // System.out
        // .println("#### DATE to keep from base calendar : " + date);
        // }

        try {

            if (statusApproved) {
                ListEffectiveCalDays = resource.getActiveCalendar()
                        .getEffectiveSpecialDays();
            } else {
                ListEffectiveCalDays = temp.getEffectiveSpecialDays();

            }

        } catch (AccessException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (PSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Map<String, List<WorkPeriod>> keepers = new HashMap<String, List<WorkPeriod>>();
        if (ListEffectiveCalDays != null) {
            for (CalDay calDay : ListEffectiveCalDays) {

                // Temps partiel?
                if (calDay.getWorkPeriods() != null) {
                    boolean unique = true;
                    for (WorkPeriod wp : (List<WorkPeriod>) calDay
                            .getWorkPeriods()) {
                        if (wp instanceof NonWorkPeriod && unique) {
                            // Logger.info("**IN** : **Date** " + calDay.getDay());
                            keepers.put(sdf.format(calDay.getDay()),
                                    (List<WorkPeriod>) calDay.getWorkPeriods());
                            unique = false;
                        }
                    }
                }
            }

        } else {
            Logger.warn("Le calendrier ne contient pas des congès saisies");
        }

        try {
            Cal activeCal = resource.getActiveCalendar();
            Cal pendingCal = new Cal();
            /*
             * Choix du calendrier à prendre en compte
             */
            if (statusApproved) {
                pendingCal = activeCal;
            } else {
                pendingCal = temp;
            }

            for (RecordCalendar recordCalendar : recordsByStatus) {
                // Logger.info(" ************************** cmatList key : "
                // + recordCalendar.getDtjre());
                try {

                    /*
                     * Verification sur la date en provenance du fichier. elle
                     * doit etre contenue entre les bornes du DateRange.
                     */
                    if (dateRange.contains(recordCalendar.getDtjre())) {

                        boolean pendingOnly = recordCalendar.getStatus() != StatusEnum.ACCEPTED;

                        /*
                         * Start
                         */
                        try {
                            resource.lock();

                            List<CalDay> listEffectiveSpecialDaysPending = pendingCal
                                    .getEffectiveSpecialDays();

                            if (listEffectiveSpecialDaysPending == null) {
                                listEffectiveSpecialDaysPending = new ArrayList<CalDay>();
                            }

                            List<CalDay> listEffectiveSpecialDaysToAddPending = new ArrayList<CalDay>();

                            List<CalDay> listEffectiveSpecialDaysToRemove = new ArrayList<CalDay>();

                            boolean demiJournée = false;
                            Map<Date, CalDay> demiJourneeTraitée = new HashMap<Date, CalDay>();

                            Map<Date, CalDay> halfDayMapPending = new HashMap<Date, CalDay>();

                            /*
                             * Traitement des absences par status
                             */
                            if (statusApproved) {
                                // System.out.println("******APPROVED*****");

                                processOfAbsences(daysToKeepUnchanged,
                                        resource, recordCalendar, pendingOnly,
                                        pendingCal, sdf,
                                        listEffectiveSpecialDaysPending,
                                        listEffectiveSpecialDaysToAddPending,
                                        listEffectiveSpecialDaysToRemove,
                                        demiJournée, demiJourneeTraitée,
                                        halfDayMapPending, true, keepers);

                            } else {
                                // System.out.println("******WAITING*****");

                                processOfAbsences(daysToKeepUnchanged,
                                        resource, recordCalendar, pendingOnly,
                                        pendingCal, sdf,
                                        listEffectiveSpecialDaysPending,
                                        listEffectiveSpecialDaysToAddPending,
                                        listEffectiveSpecialDaysToRemove,
                                        demiJournée, demiJourneeTraitée,
                                        halfDayMapPending, false, keepers);
                            }

                        } catch (AccessException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (LockException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (PermissionException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (PSException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } finally {
                            try {
                                resource.save(false);
                                resource.unlock();
                            } catch (InternalFailure e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (AccessException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (UnlockedSaveException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (PSException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }

                        /*
                         * End
                         */
                    } else {
                        Logger.warn("Absence <"
                                + recordCalendar
                                + "> non traitée car en dehors de la période de traitement");
                    }
                } catch (Exception e) {
                    Logger.error(e.getMessage(), e);
                }
            }

        } catch (AccessException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (PSException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    /**
     * @param resource
     * @param recordCalendar
     * @param pendingOnly
     * @param pendingCal
     * @param sdf
     * @param listEffectiveSpecialDaysApproved
     * @param listEffectiveSpecialDaysToAddPending
     * @param listEffectiveSpecialDaysToAddApproved
     * @param listEffectiveSpecialDaysToRemove
     * @param demiJournée
     * @param demiJourneeTraitée
     * @param halfDayMapPending
     * @param halfDayMapApproved
     * @return
     * @throws InvalidDataException
     */
    protected static void processOfAbsences(List<Date> daysToKeepUnchanged,
            Resource resource, RecordCalendar recordCalendar,
            boolean pendingOnly, Cal pendingCal, SimpleDateFormat sdf,
            List<CalDay> listEffectiveSpecialDaysPending,
            List<CalDay> listEffectiveSpecialDaysToAddPending,
            List<CalDay> listEffectiveSpecialDaysToRemove, boolean demiJournée,
            Map<Date, CalDay> demiJourneeTraitée,
            Map<Date, CalDay> halfDayMapPending, boolean statusApproved,
            Map<String, List<WorkPeriod>> keepers) throws InvalidDataException {

        try {
            // System.out.println(" ######### PROCESS 1");

            if (listEffectiveSpecialDaysPending != null) {
                for (CalDay calDay : listEffectiveSpecialDaysPending) {
                    int compt = 0;
                    if (calDay.getWorkPeriods() != null) {
                        for (WorkPeriod workPeriod : (List<WorkPeriod>) calDay
                                .getWorkPeriods()) {

                            compt++;

                        }

                        if (compt == 1) {
                            halfDayMapPending.put(roundDate(calDay).getDay(),
                                    roundDate(calDay));
                        }

                    }

                }
            } else {
                Logger.warn("Le calendrier proposé ne contient pas des congès saisies");
            }

            /*
             * Identification des demi journées April (effective special days)
             */
            List<Date> processedCalDaysActiveCal = new ArrayList<Date>();
            if (listEffectiveSpecialDaysPending != null) {
                for (CalDay calDay : listEffectiveSpecialDaysPending) {

                    if ((sdf.format(calDay.getDay()).equals(sdf
                            .format(recordCalendar.getDtjre())))) {

                        for (Date date : halfDayMapPending.keySet()) {

                            if (sdf.format(calDay.getDay()).equals(
                                    sdf.format(date))) {

                                // Logger.info(" *********DAY HALFDAY******** "
                                // + date);
                                NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(8, 0, 12, 0, "Absence");
                                if (calDay.getWorkPeriods().get(0) instanceof NonWorkPeriod) {
                                    NonWorkPeriod nwp = (NonWorkPeriod) calDay
                                            .getWorkPeriods().get(0);
                                    // Controle pour identifié les demies
                                    // journée de formation
                                    if (!"Absence".equals(nwp
                                            .getNonWorkTypeID())) {

                                        if (!processedCalDaysActiveCal
                                                .contains(calDay.getDay())) {
                                            processedCalDaysActiveCal
                                                    .add(calDay.getDay());
                                            if ((calDay.getDay())
                                                    .after(dateDebut)
                                                    && (calDay.getDay())
                                                    .before(dateFin)) {
                                                Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur le matin du <"
                                                        + calDay.getDay()
                                                        + "> sur le calendrier En cours");
                                            }
                                        }
                                        nonWorkPeriod = new NonWorkPeriod(8, 0,
                                                12, 0, nwp.getNonWorkTypeID());
                                    }
                                } else {
                                    nonWorkPeriod = new NonWorkPeriod(8, 0, 12,
                                            0, "Absence");
                                }

                                List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();

                                listWorkPeriods.add(nonWorkPeriod);

                                CalDay calDayx = new CalDay(calDay.getDay(),
                                        listWorkPeriods);
                                /*
                                 * Liste à ajouter
                                 */

                                listEffectiveSpecialDaysToAddPending
                                        .add(calDayx);

                                /*
                                 * Days to remove from Pending calendar
                                 */
                                listEffectiveSpecialDaysToRemove.add(calDay);

                                demiJournée = true;

                                demiJourneeTraitée.put(recordCalendar
                                        .getDtjre(),
                                        new CalDay(recordCalendar.getDtjre(),
                                                listWorkPeriods));

                            }
                        }

                    }

                }
            } else {
                Logger.warn("Le calendrier proposé ne contient pas des congès saisies");
            }

            /*
             * Ajout des journée d'absence complète
             */
            try {
                /*
                 * Controle pour savoir ne pas écraser les jours de congé du
                 * calendrier base
                 */
                if (!daysToKeepUnchanged.contains(DATE_FORMAT
                        .parseObject(DATE_FORMAT.format(recordCalendar
                                        .getDtjre())))) {
                    if (!demiJournée) {

                        NonWorkPeriod nonWorkPeriod = new NonWorkPeriod(8, 0,
                                12, 0, "Absence");
                        List<WorkPeriod> listWorkPeriods = new ArrayList<WorkPeriod>();

                        /*
                         * Controle pour inserer l'absence à la bonne periode de
                         * la journée
                         */
                        if (!keepers.containsKey(sdf.format(recordCalendar
                                .getDtjre()))) {

                            if (recordCalendar.getPeriode().equals(
                                    MatinApresMidiJourneeEnum.Journee)) {
                                // Logger.info(" ********** 1 ********** ");

                                listWorkPeriods.add(nonWorkPeriod);
                                listWorkPeriods.add(new NonWorkPeriod(13, 0,
                                        17, 0, "Absence"));

                            } else if (recordCalendar.getPeriode().equals(
                                    MatinApresMidiJourneeEnum.ApresMidi)) {
                                // Logger.info(" ********** 2 ********** ");

                                listWorkPeriods
                                        .add(new WorkPeriod(8, 0, 12, 0));
                                listWorkPeriods.add(new NonWorkPeriod(13, 0,
                                        17, 0, "Absence"));

                            } else {
                                // Logger.info(" ********** 3 ********** ");

                                listWorkPeriods.add(nonWorkPeriod);
                                listWorkPeriods
                                        .add(new WorkPeriod(13, 0, 17, 0));
                            }
                        } else {

                            int compt = 0;
                            int startHour = 0;
                            MatinApresMidiJourneeEnum periode = null;
                            List<WorkPeriod> wp = keepers.get(sdf
                                    .format(recordCalendar.getDtjre()));

                            if (wp != null) {
                                for (WorkPeriod workPeriod : wp) {

                                    if (workPeriod instanceof NonWorkPeriod) {
                                        startHour = workPeriod.getStartHour();
                                        /*
                                         * Periode ou inserer l'absence
                                         */
                                        // Logger.info(" ********* startHour ******** "
                                        // +
                                        // startHour);

                                        if (compt == 1) {
                                            periode = MatinApresMidiJourneeEnum.Journee;
                                        } else if (startHour < 12) {
                                            periode = MatinApresMidiJourneeEnum.Matin;
                                        } else {
                                            periode = MatinApresMidiJourneeEnum.ApresMidi;
                                        }
                                        compt++;
                                    }
                                }

                                if (periode
                                        .equals(MatinApresMidiJourneeEnum.Journee)) {
                                    // Logger.info(" ********** 1 ********** ");
                                    if ((recordCalendar.getDtjre())
                                            .after(dateDebut)
                                            && (recordCalendar.getDtjre())
                                            .before(dateFin)) {
                                        Logger.warn("Un jour d'absence a été importé sur une journée de congé autre, annulation de l'import pour le <"
                                                + recordCalendar.getDtjre()
                                                + "> ");
                                    }
                                    listWorkPeriods.addAll(wp);

                                } else if (periode
                                        .equals(MatinApresMidiJourneeEnum.Matin)) {
                                    // Logger.info(" ********** 2 ********** ");
                                    if (wp.get(0) instanceof NonWorkPeriod
                                            && recordCalendar.getPeriode() == MatinApresMidiJourneeEnum.Matin) {
                                        if ((recordCalendar.getDtjre())
                                                .after(dateDebut)
                                                && (recordCalendar.getDtjre())
                                                .before(dateFin)) {
                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur le matin du <"
                                                    + recordCalendar.getDtjre()
                                                    + "> ");
                                        }
                                        listWorkPeriods.addAll(wp);
                                    } else {

                                        listWorkPeriods.add(wp.get(0));
                                        listWorkPeriods.add(new NonWorkPeriod(
                                                13, 0, 17, 0, "Absence"));

                                    }

                                } else {
                                    // Logger.info(" ********** 3 ********** ");
                                    if (wp.get(1) instanceof NonWorkPeriod
                                            && recordCalendar.getPeriode() == MatinApresMidiJourneeEnum.ApresMidi) {
                                        if ((recordCalendar.getDtjre())
                                                .after(dateDebut)
                                                && (recordCalendar.getDtjre())
                                                .before(dateFin)) {
                                            Logger.warn("Un jour d'absence a été importé sur une demie journée de congé autre, annulation de l'import sur l'après-midi du <"
                                                    + recordCalendar.getDtjre()
                                                    + "> ");
                                        }
                                        listWorkPeriods.addAll(wp);
                                    } else {
                                        listWorkPeriods.add(new NonWorkPeriod(
                                                8, 0, 12, 0, "Absence"));
                                        listWorkPeriods.add(wp.get(1));
                                    }
                                }
                            }
                        }
                        /*
                         * Pending calendar
                         */
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(recordCalendar.getDtjre());
                        int day = calendar.get(Calendar.DAY_OF_WEEK);
                        if (!emptyDays.contains(day)) {
                            listEffectiveSpecialDaysToAddPending
                                    .add(roundDate(new CalDay(recordCalendar
                                                            .getDtjre(), listWorkPeriods)));
                        }

                    }
                }
            } catch (ParseException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            // System.out.println(" ######### PROCESS 4");
			/*
             * Remove days
             */
            listEffectiveSpecialDaysPending
                    .removeAll(listEffectiveSpecialDaysToRemove);

            /*
             * Add Pending absence days
             */
            listEffectiveSpecialDaysPending
                    .addAll(listEffectiveSpecialDaysToAddPending);

            if (listEffectiveSpecialDaysPending != null
                    && !listEffectiveSpecialDaysPending.isEmpty()) {

                pendingCal
                        .setReason(String.valueOf(System.currentTimeMillis()));
                pendingCal.setSpecialDays(listEffectiveSpecialDaysPending);

                resource.setPendingCalendar(pendingCal);

                try {

                    resource.savePendingCalendar();

                    if (statusApproved) {
                        // System.out.println("############ APPROVE PENDING CALENDAR");
                        resource.approvePendingCalendar();
                    }
                    resource.save(false);
                } catch (PSException e) {
                    Logger.error(e.getMessage(), e);
                    throw new TechnicalException(e);
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * @param resource
     * @param pendingCal
     * @param pendingCalDays
     * @param pendingCalDaysToRemove
     * @param pendingCalDaysToAdd
     * @throws InvalidDataException
     * @throws UnlockedSaveException
     * @throws PSException
     */
    protected static void removeAbsence(Resource resource, Cal pendingCal,
            List<CalDay> pendingCalDays, List<CalDay> pendingCalDaysToRemove,
            List<CalDay> pendingCalDaysToAdd, boolean statusApproved)
            throws InvalidDataException, UnlockedSaveException, PSException {

        try {
            for (CalDay calDay : pendingCalDays) {
                /*
                 * Contrôl pour savoir si la date se trouve dans la période à
                 * traitée
                 */

                if (dateRange.contains(calDay.getDay())) {

                    // Logger.info("************************ REMOVE ABSENCE calDay.getDay() : "
                    // + calDay.getDay());
                    List<WorkPeriod> workPeriods = calDay.getWorkPeriods();
                    if (calDay.getWorkPeriods() != null) {
                        /*
                         * List workPeriods to insert in the new calDay
                         */
                        List<WorkPeriod> workPeriodsToAdd = new ArrayList<WorkPeriod>();

                        for (WorkPeriod workPeriod : workPeriods) {
                            /*
                             * boolean pour identifier si on efface ou on
                             * conserve le workperiod
                             */
                            boolean instanceOfNonWorkPeriod = false;
                            /*
                             * On remplace l'absence par un workPeriod
                             */
                            if (workPeriod instanceof NonWorkPeriod) {
                                // Logger.info("************************ µINSTANCE OF NON WORKING PERIOD {"

                                if ("Absence"
                                        .equals(((NonWorkPeriod) workPeriod)
                                                .getNonWorkTypeID())) {
                                    // Logger.info(" ¤¤¤¤¤ Replace absence ");

                                    if (!pendingCalDaysToRemove
                                            .contains(calDay)) {
                                        pendingCalDaysToRemove.add(calDay);
                                    }
                                    workPeriodsToAdd.add(new WorkPeriod(
                                            workPeriod.getStartHour(),
                                            workPeriod.getStartMinute(),
                                            workPeriod.getFinishHour(),
                                            workPeriod.getFinishMinute()));

                                } else {
                                    workPeriodsToAdd.add(workPeriod);
                                }

                                instanceOfNonWorkPeriod = true;
                            }
                            /*
                             * On conserve le workPeriod existant
                             */
                            if (!instanceOfNonWorkPeriod) {
                                // Logger.info(" ¤¤¤¤¤ Conserve workPeriod ");
                                workPeriodsToAdd.add(workPeriod);
                            }

                        }

                        if (workPeriodsToAdd.size() == 1) {
                            pendingCalDaysToAdd.add(new CalDay(calDay.getDay(),
                                    workPeriodsToAdd));
                        } else {
                            for (WorkPeriod workPeriod : workPeriodsToAdd) {
                                if (workPeriod instanceof NonWorkPeriod) {
                                    pendingCalDaysToAdd.add(new CalDay(calDay
                                            .getDay(), workPeriodsToAdd));
                                    break;
                                }
                            }
                        }

                    }
                }
            }

            pendingCalDays.removeAll(pendingCalDaysToRemove);

            pendingCalDays.addAll(pendingCalDaysToAdd);

            pendingCal.setSpecialDays(pendingCalDays);

            resource.setPendingCalendar(pendingCal);
            resource.savePendingCalendar();
            if (statusApproved) {
                /*
                 * test
                 */
                // System.out
                // .println("############ REMOVE ABSENCES & APPROVE PENDING CALENDAR");
                resource.approvePendingCalendar();

                /*
                 * 
                 */
            }

            resource.save(false);

            Cal test = resource.getPendingCalendar();
            List<CalDay> days = test.getEffectiveSpecialDays();

        } catch (Exception e) {
            // TODO: handle exception
        } finally {
            resource.unlock();
        }
    }

    public static CalDay roundDate(CalDay calDay) {

        Date date = calDay.getDay();
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTimeZone(mSession.getServerTimeZone());
        } catch (PSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Logger.error("ERR -  Une erreur est survenue en récupérant le fuseau horaire du système ");
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
