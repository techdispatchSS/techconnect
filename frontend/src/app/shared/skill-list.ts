/** Most skills one application may list, and the longest any single one may be. */
export const MAX_SKILLS = 20;
export const MAX_SKILL_LENGTH = 40;

/**
 * Splits skills text into one entry per skill. New applications store one skill per line; older
 * ones were typed as a comma-separated sentence, so commas split too. Blanks are dropped and a
 * skill that repeats (ignoring case) is kept once.
 */
export function parseSkills(text: string | null | undefined): string[] {
  const seen = new Set<string>();
  const skills: string[] = [];
  for (const part of (text ?? '').split(/[\n,]+/)) {
    const skill = part.trim().replace(/\s+/g, ' ').slice(0, MAX_SKILL_LENGTH).trim();
    if (skill && !seen.has(skill.toLowerCase())) {
      seen.add(skill.toLowerCase());
      skills.push(skill);
    }
  }
  return skills;
}

/** The value sent to the server: one skill per line, or null when there are none. */
export function joinSkills(skills: readonly string[]): string | null {
  return skills.length > 0 ? skills.join('\n') : null;
}
