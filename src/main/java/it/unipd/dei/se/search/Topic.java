package it.unipd.dei.se.search;

public class Topic {
    private int number;
    private String title;
    private String description;
    private String narrative;

    public Topic(int number, String title, String description, String narrative){
        this.number = number;
        this.title = title;
        this.description = description;
        this.narrative = narrative;
    }

    public Topic(){
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description.replace("\n", "").replace("\r", "");
    }

    public void setNarrative(String narrative) {
        this.narrative = narrative.replace("\n", "").replace("\r", "");
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getNarrative() {
        return narrative;
    }

    @Override
    public String toString() {
        String str = "number : " + number + "\ntitle : " + title + "\ndescription : " + description + "\nnarrative : " + narrative;
        return str;
    }
}
