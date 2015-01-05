angular.module('images.controllers', ['ui.bootstrap'])

.controller(
		'ImageController',
		function($scope, $modal) {
			// Initialize data fields
			$scope.serverTime = "Nothing Here Yet";

			// Statistics
			$scope.fileCount = 0;
			$scope.imageBytes = 0;
			$scope.storageBytes = 0;

			// Image Gallery objects
			$scope.images = [];

			// events from server handler
			$scope.handleServerEvent = function(e) {
				$scope.$apply(function() {
					var raw = JSON.parse(e.data);
					var target = raw.target;
					var data = raw.data;
					// console.log("Received data for " + target);
					if (target == "image") {
						var img = {
							id : data.id,	
							tooltip : data.tooltip,
							description : data.description,
							fileName : data.fileName,
							size : data.size / 1024,
							storage: data.storage / 1024,
							format: data.format,
							contentType : data.contentType
						}
						console.log("thumb - " + img.src);
						$scope.images.unshift(img);
					} else if (target == "statistics") {
						$scope.fileCount = data.files;
						$scope.imageBytes = data.size / (1024 * 1024);
						$scope.storageBytes = data.storage / (1024 * 1024);
						$scope.serverTime = data.serverTime;
					}
				});
			};

			// sse socket
			$scope.startSocket = function(uuid) {
				$scope.stopSocket();
				$scope.images = [];
				var url = "/images/sse/" + uuid
				console.log(url);
				$scope.socket = new EventSource(url);
				$scope.socket.addEventListener("message",
						$scope.handleServerEvent, false);
			};

			$scope.stopSocket = function() {
				if (typeof $scope.socket != 'undefined') {
					$scope.socket.close();
				}
			};

			// Modal window
//			$scope.items = [ 'item1', 'item2', 'item3' ];

			$scope.open = function(size, img) {

				var modalInstance = $modal.open({
					templateUrl : 'myModalContent.html',
					controller : 'ImageModalInstanceCtrl',
					size : size,
					resolve : {
						image : function() {
							console.log("Img " + img)
							return img;
						}
						
					}
				});

//				modalInstance.result.then(function(selectedItem) {
//					$scope.selected = selectedItem;
//				}, function() {
//					$log.info('Modal dismissed at: ' + new Date());
//				});
			};
		})
		
.controller('ImageModalInstanceCtrl', function ($scope, $modalInstance, image) {

  $scope.image = image;
  console.log("Image.id" + image.id)

  $scope.ok = function () {
    $modalInstance.close("");
  };

  $scope.cancel = function () {
    $modalInstance.dismiss('cancel');
  };
})

/*
 * This directive allows us to pass a function in on an enter key to do what we
 * want.
 */
.directive('ngEnter', function() {
	return function(scope, element, attrs) {
		element.bind("keydown keypress", function(event) {
			if (event.which === 13) {
				scope.$apply(function() {
					scope.$eval(attrs.ngEnter);
				});

				event.preventDefault();
			}
		});
	};
});
