package dev.ake.platform.store.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stories")
public class StoryEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    private String persona;

    @Column(length = 2000)
    private String action;

    @Column(length = 2000)
    private String benefit;

    @Column(length = 8000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "story_criteria", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "criterion", length = 2000)
    private List<String> acceptanceCriteria = new ArrayList<>();

    private String priority;
    private String status;
    private int points;
    private double confidence;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "story_risks", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "risk")
    private List<String> risks = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "story_rationale", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "note", length = 1000)
    private List<String> rationale = new ArrayList<>();

    @Column(length = 8000)
    private String citationsJson;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "story_notes", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "note", length = 1000)
    private List<String> notes = new ArrayList<>();

    private String model;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBenefit() {
        return benefit;
    }

    public void setBenefit(String benefit) {
        this.benefit = benefit;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public List<String> getRationale() {
        return rationale;
    }

    public void setRationale(List<String> rationale) {
        this.rationale = rationale;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson) {
        this.citationsJson = citationsJson;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
