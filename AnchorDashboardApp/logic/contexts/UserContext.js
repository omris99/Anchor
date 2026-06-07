import { createContext } from "react";

// Shared user/auth context. Lives in its own leaf module (imports nothing from
// App.js) so screens can consume it without creating an App.js <-> screens
// require cycle.
export const UserContext = createContext(null);
