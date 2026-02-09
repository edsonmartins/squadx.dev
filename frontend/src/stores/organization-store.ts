import { create } from "zustand";
import { persist } from "zustand/middleware";
import {
  organizationsApi,
  type OrganizationResponse,
  type CreateOrganizationRequest,
  type UpdateOrganizationRequest,
} from "@/lib/api";

interface OrganizationState {
  organizations: OrganizationResponse[];
  selectedOrganization: OrganizationResponse | null;
  isLoading: boolean;
  error: string | null;

  fetchOrganizations: () => Promise<void>;
  fetchOrganization: (id: number) => Promise<void>;
  createOrganization: (data: CreateOrganizationRequest) => Promise<OrganizationResponse>;
  updateOrganization: (id: number, data: UpdateOrganizationRequest) => Promise<OrganizationResponse>;
  deleteOrganization: (id: number) => Promise<void>;
  selectOrganization: (organization: OrganizationResponse | null) => void;
  clearError: () => void;
}

export const useOrganizationStore = create<OrganizationState>()(
  persist(
    (set, get) => ({
      organizations: [],
      selectedOrganization: null,
      isLoading: false,
      error: null,

      fetchOrganizations: async () => {
        set({ isLoading: true, error: null });
        try {
          const response = await organizationsApi.list();
          const organizations = response.content;
          set({ organizations, isLoading: false });

          // Auto-select first organization if none selected
          if (organizations.length > 0 && !get().selectedOrganization) {
            set({ selectedOrganization: organizations[0] });
          }
        } catch (error) {
          set({ error: (error as Error).message, isLoading: false });
        }
      },

      fetchOrganization: async (id: number) => {
        set({ isLoading: true, error: null });
        try {
          const organization = await organizationsApi.get(id);
          set({ selectedOrganization: organization, isLoading: false });
        } catch (error) {
          set({ error: (error as Error).message, isLoading: false });
        }
      },

      createOrganization: async (data: CreateOrganizationRequest) => {
        set({ isLoading: true, error: null });
        try {
          const newOrg = await organizationsApi.create(data);
          set((state) => ({
            organizations: [...state.organizations, newOrg],
            isLoading: false,
          }));

          // Select newly created org if it's the first one
          if (get().organizations.length === 1) {
            set({ selectedOrganization: newOrg });
          }

          return newOrg;
        } catch (error) {
          set({ error: (error as Error).message, isLoading: false });
          throw error;
        }
      },

      updateOrganization: async (id: number, data: UpdateOrganizationRequest) => {
        set({ isLoading: true, error: null });
        try {
          const updatedOrg = await organizationsApi.update(id, data);
          set((state) => ({
            organizations: state.organizations.map((o) =>
              o.id === id ? updatedOrg : o
            ),
            selectedOrganization:
              state.selectedOrganization?.id === id
                ? updatedOrg
                : state.selectedOrganization,
            isLoading: false,
          }));
          return updatedOrg;
        } catch (error) {
          set({ error: (error as Error).message, isLoading: false });
          throw error;
        }
      },

      deleteOrganization: async (id: number) => {
        set({ isLoading: true, error: null });
        try {
          await organizationsApi.delete(id);
          const remaining = get().organizations.filter((o) => o.id !== id);
          set({
            organizations: remaining,
            selectedOrganization:
              get().selectedOrganization?.id === id
                ? remaining[0] || null
                : get().selectedOrganization,
            isLoading: false,
          });
        } catch (error) {
          set({ error: (error as Error).message, isLoading: false });
          throw error;
        }
      },

      selectOrganization: (organization: OrganizationResponse | null) => {
        set({ selectedOrganization: organization });
      },

      clearError: () => {
        set({ error: null });
      },
    }),
    {
      name: "squadx-organization",
      partialize: (state) => ({
        selectedOrganization: state.selectedOrganization,
      }),
    }
  )
);
