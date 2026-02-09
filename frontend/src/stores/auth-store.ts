import { create } from "zustand";
import { persist } from "zustand/middleware";
import { api, authApi, type UserResponse, type AuthResponse } from "@/lib/api";

interface AuthState {
  user: UserResponse | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  login: (email: string, password: string) => Promise<void>;
  register: (
    email: string,
    password: string,
    fullName: string
  ) => Promise<void>;
  logout: () => void;
  refreshTokens: () => Promise<void>;
  setUser: (user: UserResponse) => void;
  initialize: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: true,

      login: async (email: string, password: string) => {
        const response = await authApi.login(email, password);
        handleAuthResponse(response, set);
      },

      register: async (
        email: string,
        password: string,
        fullName: string
      ) => {
        const response = await authApi.register(email, password, fullName);
        handleAuthResponse(response, set);
      },

      logout: () => {
        api.setToken(null);
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
        });
      },

      refreshTokens: async () => {
        const { refreshToken } = get();
        if (!refreshToken) {
          throw new Error("No refresh token available");
        }

        const response = await authApi.refresh(refreshToken);
        handleAuthResponse(response, set);
      },

      setUser: (user: UserResponse) => {
        set({ user });
      },

      initialize: async () => {
        const { accessToken, refreshToken } = get();

        if (!accessToken) {
          set({ isLoading: false });
          return;
        }

        api.setToken(accessToken);

        try {
          const user = await authApi.me();
          set({ user, isAuthenticated: true, isLoading: false });
        } catch (error) {
          // Try to refresh token
          if (refreshToken) {
            try {
              await get().refreshTokens();
              set({ isLoading: false });
            } catch {
              get().logout();
              set({ isLoading: false });
            }
          } else {
            get().logout();
            set({ isLoading: false });
          }
        }
      },
    }),
    {
      name: "squadx-auth",
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
      }),
    }
  )
);

function handleAuthResponse(
  response: AuthResponse,
  set: (state: Partial<AuthState>) => void
) {
  api.setToken(response.access_token);
  set({
    user: response.user,
    accessToken: response.access_token,
    refreshToken: response.refresh_token,
    isAuthenticated: true,
  });
}
