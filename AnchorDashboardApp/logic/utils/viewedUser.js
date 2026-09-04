// The id of the person whose data a screen should show: the elder's own id
// when they're logged in directly, or the linked elder's id when a family
// member is viewing on their behalf (null if not linked to anyone yet).
export function getViewedUserId(user) {
    if (!user) return null;
    return user.userType === 'elderly' ? user.userId : user.linkedElderId ?? null;
}
