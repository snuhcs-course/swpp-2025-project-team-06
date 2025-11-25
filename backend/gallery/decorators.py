import logging
from functools import wraps
from rest_framework.response import Response
from rest_framework import status
from gallery.models import Photo, Tag
import time
import uuid

logger = logging.getLogger(__name__)

def log_request(view_func):
    """
    Decorator to log all incoming API requests.

    Logs: HTTP method, path, user ID, and execution time.
    """
    @wraps(view_func)
    def wrapper(self, request, *args, **kwargs):

        user_id = request.user.id if request.user.is_authenticated else "anonymous"
        logger.info(
            f"[REQUEST] {request.method} {request.path} - User: {user_id}"
        )

        start_time = time.time()
        response = view_func(self, request, *args, **kwargs)
        execution_time = (time.time() - start_time) * 1000  # ms

        logger.info(
            f"[RESPONSE] {request.method} {request.path} - "
            f"Status: {response.status_code} - Time: {execution_time:.2f}ms"
        )

        return response

    return wrapper

def validate_pagination(max_limit=100):
    """
    Decorator to validate and normalize pagination parameters.

    Validates:
    - offset >= 0
    - 1 <= limit <= max_limit

    Adds validated values to request object:
    - request.validated_offset
    - request.validated_limit
    """
    def decorator(view_func):
        @wraps(view_func)
        def wrapper(self, request, *args, **kwargs):
            try:
                offset = int(request.GET.get("offset", 0))
                limit = int(request.GET.get("limit", 20))

                if offset < 0:
                    return Response(
                        {"error": "offset must be >= 0"},
                        status=status.HTTP_400_BAD_REQUEST
                    )

                if limit < 1 or limit > max_limit:
                    return Response(
                        {"error": f"limit must be between 1 and {max_limit}"},
                        status=status.HTTP_400_BAD_REQUEST
                    )

                # Add validated values to request
                request.validated_offset = offset
                request.validated_limit = limit

                return view_func(self, request, *args, **kwargs)

            except ValueError:
                return Response(
                    {"error": "offset and limit must be integers"},
                    status=status.HTTP_400_BAD_REQUEST
                )

        return wrapper
    return decorator

def handle_exceptions(view_func):
    """
    Decorator for centralized exception handling.

    Handles:
    - Photo.DoesNotExist / Tag.DoesNotExist -> 404
    - ValidationError -> 400
    - PermissionDenied -> 403
    - Generic exceptions -> 500 (with logging)
    """
    @wraps(view_func)
    def wrapper(self, request, *args, **kwargs):
        try:
            return view_func(self, request, *args, **kwargs)

        except Photo.DoesNotExist:
            logger.warning(
                f"Photo not found - User: {request.user.id}, "
                f"Path: {request.path}"
            )
            return Response(
                {"error": "Photo not found"},
                status=status.HTTP_404_NOT_FOUND
            )

        except Tag.DoesNotExist:
            logger.warning(
                f"Tag not found - User: {request.user.id}, "
                f"Path: {request.path}"
            )
            return Response(
                {"error": "Tag not found"},
                status=status.HTTP_404_NOT_FOUND
            )

        except PermissionError:
            logger.warning(
                f"Permission denied - User: {request.user.id}, "
                f"Path: {request.path}"
            )
            return Response(
                {"error": "Permission denied"},
                status=status.HTTP_403_FORBIDDEN
            )

        except ValueError as e:
            logger.warning(f"Validation error: {str(e)}")
            return Response(
                {"error": str(e)},
                status=status.HTTP_400_BAD_REQUEST
            )

        except Exception as e:
            # Log full error with stack trace
            logger.error(
                f"Unexpected error in {view_func.__name__}: {str(e)}",
                exc_info=True,
                extra={
                    'user_id': request.user.id,
                    'path': request.path,
                    'method': request.method
                }
            )
            # Return safe error message to client
            return Response(
                {"error": "Internal server error"},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR
            )

    return wrapper

def validate_uuid(param_name):
    """
    Decorator to validate UUID path parameters.

    Args:
        param_name: Name of the URL parameter to validate (e.g., 'photo_id')
    """
    def decorator(view_func):
        @wraps(view_func)
        def wrapper(self, request, *args, **kwargs):

            uuid_value = kwargs.get(param_name)

            if not uuid_value:
                return Response(
                    {"error": f"{param_name} is required"},
                    status=status.HTTP_400_BAD_REQUEST
                )

            try:
                # Validate UUID format
                uuid.UUID(str(uuid_value))
                return view_func(self, request, *args, **kwargs)
            except ValueError:
                return Response(
                    {"error": f"Invalid {param_name} format"},
                    status=status.HTTP_400_BAD_REQUEST
                )

        return wrapper
    return decorator

def require_ownership(model_class, param_name, lookup_field='pk'):
    """
    Decorator to verify resource ownership.

    Args:
        model_class: Model class (e.g., Photo, Tag)
        param_name: URL parameter name (e.g., 'photo_id')
        lookup_field: Model field to lookup (default: 'pk')
    """
    def decorator(view_func):
        @wraps(view_func)
        def wrapper(self, request, *args, **kwargs):
            resource_id = kwargs.get(param_name)

            # Check ownership at DB level (security best practice)
            lookup = {lookup_field: resource_id, 'user': request.user}

            if not model_class.objects.filter(**lookup).exists():
                logger.warning(
                    f"Unauthorized access attempt - User: {request.user.id}, "
                    f"Resource: {model_class.__name__}({resource_id})"
                )
                return Response(
                    {"error": f"{model_class.__name__} not found"},
                    status=status.HTTP_404_NOT_FOUND
                )

            return view_func(self, request, *args, **kwargs)

        return wrapper
    return decorator