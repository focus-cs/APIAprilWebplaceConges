package fr.sciforma.beans;

public class Connector {

    private String IP;
    private String PORT;
    private String CONTEXTE;
    private String USER;
    private String PWD;
    private String DRIVER;

    public Connector() {
        super();
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public String getIP() {
        return IP;
    }

    public void setPORT(String PORT) {
        this.PORT = PORT;
    }

    public String getPORT() {
        return PORT;
    }

    public void setCONTEXTE(String CONTEXTE) {
        this.CONTEXTE = CONTEXTE;
    }

    public String getCONTEXTE() {
        return CONTEXTE;
    }

    public void setUSER(String USER) {
        this.USER = USER;
    }

    public String getUSER() {
        return USER;
    }

    public void setPWD(String PWD) {
        this.PWD = PWD;
    }

    public String getPWD() {
        return PWD;
    }

    public String getDRIVER() {
        return DRIVER;
    }

    public void setDRIVER(String DRIVER) {
        this.DRIVER = DRIVER;
    }


}
