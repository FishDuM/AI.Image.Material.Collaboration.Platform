export const TEAM_MEMBER_ROLE = {
  OWNER: 1,
  MEMBER: 2,
}

export const TEAM_ROLE_OPTIONS = [
  { value: TEAM_MEMBER_ROLE.OWNER, label: '所有者' },
  { value: TEAM_MEMBER_ROLE.MEMBER, label: '成员' },
]

export const TEAM_ROLE_LABELS = {
  [TEAM_MEMBER_ROLE.OWNER]: '所有者',
  [TEAM_MEMBER_ROLE.MEMBER]: '成员',
}
