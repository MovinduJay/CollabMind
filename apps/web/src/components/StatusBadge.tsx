import React from "react";
type StatusBadgeProps = {
  label: string;
  status: "UP" | "DOWN" | "CHECKING" | "CONNECTED" | "DISCONNECTED" | "CONNECTING";
};

export function StatusBadge({ label, status }: StatusBadgeProps) {
  return (
    <div className={`status-badge ${status.toLowerCase()}`}>
      <span />
      <strong>{label}</strong>
      <em>{status}</em>
    </div>
  );
}

