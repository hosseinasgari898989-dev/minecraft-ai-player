export interface SubtaskItem { skillId: string; parameter: string; targetCount: number; completedCount: number; status: string; }
export interface PlannedTask { id: string; intent: string; priority: number; status: string; failureReason?: string; summary: string; requiredResources?: Record<string, number>; subtasks: SubtaskItem[]; }
export const PRIORITY: Record<string, number>;
export class GoalManager { activeTask: PlannedTask | null; suspendedTask: PlannedTask | null; lastFailure: string; lastOutcomeMessage: string; assignIntent(intent: unknown, worldSnapshot: Record<string, unknown>, pvpAllowed?: boolean, targetApproved?: boolean): PlannedTask | null; evaluatePriorities(worldSnapshot: Record<string, unknown>): void; }
