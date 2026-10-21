export interface BlueprintBlock { dx: number; dy: number; dz: number; blockId: string; optional: boolean; }
export interface BlueprintDef { id: string; displayName: string; width: number; height: number; depth: number; blocks: BlueprintBlock[]; requiredMaterials: Record<string, number>; }
export const BLUEPRINTS: Record<string, BlueprintDef>;
export function getBlueprint(styleId?: string | null): BlueprintDef;
