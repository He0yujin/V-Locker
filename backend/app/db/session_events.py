
from sqlalchemy import event, insert, inspect

from backend.app.db.models import V2LSession, V2LSessionEvent


@event.listens_for(V2LSession, "after_insert")
def record_session_creation(mapper, connection, target):
    """세션 생성 이력 기록"""

    connection.execute(
        insert(V2LSessionEvent).values(
            session_id=target.id,
            event_type="SESSION_STARTED",
            current_soc=target.current_soc,
            new_target_soc=target.target_soc,
            description="V2L session started",
        )
    )


@event.listens_for(V2LSession, "after_update")
def record_session_update(mapper, connection, target):
    """세션 상태 및 설정 변경 이력 기록"""

    state = inspect(target)

    soc = state.attrs.current_soc.history
    target_soc = state.attrs.target_soc.history
    status = state.attrs.v2l_status.history
    destination = state.attrs.destination_name.history

    if not any([
        soc.has_changes(),
        target_soc.has_changes(),
        status.has_changes(),
        destination.has_changes(),
    ]):
        return

    def previous(history, current):
        return history.deleted[0] if history.deleted else current

    if status.has_changes() and target.v2l_status == "LOCKED_OUT":
        event_type = "LOCKED_OUT"
    elif target_soc.has_changes() or destination.has_changes():
        event_type = "SESSION_EXTENDED"
    elif soc.has_changes():
        event_type = "SOC_UPDATED"
    else:
        event_type = "STATUS_CHANGED"

    connection.execute(
        insert(V2LSessionEvent).values(
            session_id=target.id,
            event_type=event_type,
            previous_soc=previous(soc, target.current_soc),
            current_soc=target.current_soc,
            previous_target_soc=previous(target_soc, target.target_soc),
            new_target_soc=target.target_soc,
            description=(
                f"{previous(status, target.v2l_status)}"
                f" -> {target.v2l_status}"
            ),
        )
    )
