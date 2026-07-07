package com.ghost.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("workspace_jobs")
public class WorkspaceJobDocument {

    @Id
    private String id;
    private String kind;
    private String sourceLabel;
    private String instruction;
    private String tree;
    private String stackProfileJson;
    private String securityAuditJson;
    private String analysis;
    private String status;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getTree() {
        return tree;
    }

    public void setTree(String tree) {
        this.tree = tree;
    }

    public String getStackProfileJson() {
        return stackProfileJson;
    }

    public void setStackProfileJson(String stackProfileJson) {
        this.stackProfileJson = stackProfileJson;
    }

    public String getSecurityAuditJson() {
        return securityAuditJson;
    }

    public void setSecurityAuditJson(String securityAuditJson) {
        this.securityAuditJson = securityAuditJson;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
