import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { WorkspacePage } from "./features/workspace/WorkspacePage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<WorkspacePage />} />
      <Route path="/r/:conversationId" element={<WorkspacePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
