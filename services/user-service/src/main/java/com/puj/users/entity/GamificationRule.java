package com.puj.users.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "gamification_rules", schema = "users")
public class GamificationRule {

    @Id
    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public String  getActionType()  { return actionType; }
    public int     getPoints()      { return points; }
    public String  getDescription() { return description; }
    public boolean isActive()       { return active; }

    public void setActionType(String actionType)   { this.actionType = actionType; }
    public void setPoints(int points)              { this.points = points; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active)          { this.active = active; }
}
