export interface GridPoint { x: number; y: number; z: number; }
export interface PathOutput { reachable: boolean; partial: boolean; waypoints: GridPoint[]; nodesExpanded: number; }
export function findLocalPath(grid: { isPassableBody: (x:number,y:number,z:number)=>boolean; isSolidGround:(x:number,y:number,z:number)=>boolean; isHazard:(x:number,y:number,z:number)=>boolean; isWater:(x:number,y:number,z:number)=>boolean; }, start:GridPoint, goal:GridPoint, acceptRadius?:number, maxNodes?:number):PathOutput;
